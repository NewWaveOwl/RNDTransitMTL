package transit

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI

private const val fareSource="https://donnees.artm.quebec/catalogue/dataset/9b19ddd0-f10d-45b9-9cee-8896bacc012d/resource/88c53302-58f7-4d35-9231-dc3d48dce008/download/zone_tarifaire_2026.geojson"
internal class Upstream : AutoCloseable {
    private val client=HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis=30_000; connectTimeoutMillis=5_000 }; followRedirects=true }
    suspend fun request(url: String, body: JsonObject?=null, headers: Map<String,String> = emptyMap()): HttpResponse {
        require(URI(url).scheme in listOf("http","https")) { "Endpoint must use HTTP(S)." }
        return client.request(url) { method=if(body==null) HttpMethod.Get else HttpMethod.Post
            headers.forEach { (name,v) -> header(name,v) }
            if(body!=null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
        }
    }
    suspend fun json(url: String, body: JsonObject?=null): JsonObject {
        val response=request(url,body)
        if(!response.status.isSuccess()) throw ProviderError("Upstream returned HTTP ${response.status.value}.")
        return try { transit.json.parseToJsonElement(response.bodyAsText()).jsonObject } catch(e: Exception) { throw ProviderError("Invalid upstream JSON.",e) }
    }
    override fun close()=client.close()
}
internal suspend fun loadZones(path: Path, upstream: Upstream): FareZones {
    if(Files.exists(path)) return FareZones(json.parseToJsonElement(Files.readString(path)))
    val response=upstream.request(fareSource)
    if(!response.status.isSuccess()) throw ProviderError("Could not download ARTM zones: HTTP ${response.status.value}")
    val text=response.bodyAsText(); val zones=FareZones(json.parseToJsonElement(text))
    withContext(Dispatchers.IO) { path.toAbsolutePath().parent?.let { Files.createDirectories(it) }; Files.writeString(path,text) }
    return zones
}
/** HTTP boundary is separate from generation so policy tests need no server sockets. */
internal class Api(private val generator: Generator, private val authorize: suspend (String?)->Boolean = { true }) {
    private val capacity=Semaphore(4)
    suspend fun handle(contentType: String?, authorization: String?, read: suspend ()->String): Pair<Int,JsonObject> {
        if(!capacity.tryAcquire()) return 429 to obj("status" to "unavailable","message" to "Trip planner is busy. Try again.")
        try {
            return withTimeout(40_000) {
                if(!authorize(authorization)) return@withTimeout 401 to obj("status" to "unauthorized")
                checkInput(contentType?.substringBefore(';')?.trim()=="application/json","Send application/json.")
                val text=read(); checkInput(text.toByteArray().size<=16_384,"Request is too large.")
                val body=try { json.parseToJsonElement(text) } catch(_: Exception) { throw InputError("Invalid JSON request.") }
                200 to generator.generate(parseRequest(body))
            }
        } catch(e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); return 503 to obj("status" to "unavailable","message" to "Request timed out.") }
        catch(e: CancellationException) { throw e }
        catch(e: InputError) { return 400 to obj("status" to "invalid_input","message" to e.message) }
        catch(e: Exception) { return 503 to obj("status" to "unavailable","message" to "Routing service unavailable. Try again.") }
        finally { capacity.release() }
    }
}
internal fun Application.generationRoutes(api: Api) {
    routing {
        get("/health") { call.respondText(obj("status" to "ready").toString(),ContentType.Application.Json) }
        post("/v1/trips/generate") {
            val (status,result)=api.handle(call.request.headers[HttpHeaders.ContentType],call.request.headers[HttpHeaders.Authorization]) {
                val channel=call.receiveChannel(); val output=ByteArrayOutputStream(); val buffer=ByteArray(4096)
                while(true) {
                    val size=channel.readAvailable(buffer,0,buffer.size)
                    if(size<0) break
                    checkInput(output.size()+size<=16_384,"Request is too large.")
                    output.write(buffer,0,size)
                }
                output.toString(Charsets.UTF_8.name())
            }
            call.response.headers.append(HttpHeaders.CacheControl,"no-store")
            call.respondText(result.toString(),ContentType.Application.Json,HttpStatusCode.fromValue(status))
        }
    }
}
fun main() {
    fun env(name: String, default: String)=System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
    val host=env("HOST","127.0.0.1"); val port=env("PORT","18088").toInt().also { require(it in 1..65535) }
    val endpoint=env("OTP_URL","http://127.0.0.1:18086/otp/gtfs/v1"); val catalogue=env("OTP_CATALOGUE_URL",endpoint)
    val supabase=System.getenv("SUPABASE_URL"); val key=System.getenv("SUPABASE_PUBLISHABLE_KEY")
    val authenticated=!supabase.isNullOrBlank() && !key.isNullOrBlank()
    require(host in listOf("127.0.0.1","::1","localhost") || authenticated) { "Public binding requires Supabase authentication." }
    // PUBLIC_API also protects loopback services published through a reverse tunnel.
    require(env("PUBLIC_API","false")!="true" || authenticated) { "PUBLIC_API requires Supabase authentication." }
    if(authenticated) require(URI(supabase).scheme=="https") { "SUPABASE_URL must use HTTPS." }
    Upstream().use { upstream ->
        val (zones,routes)=runBlocking {
            val zones=try { loadZones(Path.of(env("FARE_ZONES_FILE","data/fare-zones-2026.geojson")),upstream) }
                catch(e: Exception) { throw IllegalStateException("Could not load ARTM fare zones: ${e.message}",e) }
            val response=try {
                val enriched=upstream.json(catalogue,obj("query" to "query RoutingLines { routes { gtfsId mode shortName longName stops { lat lon } } }"))
                if(enriched["errors"].items().isEmpty()) enriched else
                    upstream.json(catalogue,obj("query" to "query RoutingLines { routes { gtfsId mode shortName longName } }"))
            }
                catch(e: Exception) { throw IllegalStateException("OTP catalogue unavailable at $catalogue. Start Start-StmRealtimeStack.ps1, then restart the backend.",e) }
            require(response["errors"].items().isEmpty() && response.field("data").field("routes") is JsonArray) { "Invalid OTP route catalogue." }
            zones to response.field("data").field("routes").items()
        }
        val generator=Generator(routes,zones,{ body -> upstream.json(endpoint,body) })
        val api=Api(generator) { token ->
            if(!authenticated) true else if(token?.startsWith("Bearer ")!=true) false else {
                val response=withTimeout(5000) { upstream.request(URI(supabase).resolve("/auth/v1/user").toString(),headers=mapOf("Authorization" to token,"apikey" to key!!)) }
                if(response.status.value>=500) throw ProviderError("Authentication service unavailable.")
                response.status.isSuccess()
            }
        }
        println("Trip generation ready at http://$host:$port (Kotlin/Ktor; random geographic destinations).")
        embeddedServer(CIO,port=port,host=host) { generationRoutes(api) }.start(wait=true)
    }
}
