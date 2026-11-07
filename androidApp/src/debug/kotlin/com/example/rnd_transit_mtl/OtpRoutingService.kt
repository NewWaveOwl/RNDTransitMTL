package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.routing.DebugTripLoader
import com.example.rnd_transit_mtl.data.routing.OtpTripDemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun stmTripLoader(): DebugTripLoader = debugLoader(OtpTripDemo.StmBus)
internal fun exoTripLoader(): DebugTripLoader = debugLoader(OtpTripDemo.StmExo)

private fun debugLoader(demo: OtpTripDemo): DebugTripLoader = demo.loader(::postToOtp) {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Toronto")
    }.format(Date())
}

private suspend fun postToOtp(body: String): String = withContext(Dispatchers.IO) {
    val connection = URL(BuildConfig.STM_OTP_ENDPOINT).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        check(connection.responseCode in 200..299) { "OTP returned HTTP ${connection.responseCode}." }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
