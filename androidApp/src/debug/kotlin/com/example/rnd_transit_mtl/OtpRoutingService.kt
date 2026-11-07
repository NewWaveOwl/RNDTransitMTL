package com.example.rnd_transit_mtl

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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

internal fun stmTripLoader(context: Context): DebugTripLoader? = debugLoader(context, OtpTripDemo.StmBus)
internal fun exoTripLoader(context: Context): DebugTripLoader? = debugLoader(context, OtpTripDemo.StmExo)

private fun debugLoader(context: Context, demo: OtpTripDemo): DebugTripLoader {
    val endpoint by lazy {
        val application = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        checkNotNull(application.metaData?.getString("com.example.rnd_transit_mtl.STM_OTP_ENDPOINT")) {
            "OTP endpoint is missing from the debug manifest."
        }
    }
    return demo.loader(
        post = { body -> postToOtp(endpoint, body) },
        departure = {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("America/Toronto")
            }.format(Date())
        },
        reportFailure = { Log.w("RndTransitOtp", "Failed to load ${demo.name}", it) }
    )
}

private suspend fun postToOtp(endpoint: String, body: String): String = withContext(Dispatchers.IO) {
    val connection = URL(endpoint).openConnection() as HttpURLConnection
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
