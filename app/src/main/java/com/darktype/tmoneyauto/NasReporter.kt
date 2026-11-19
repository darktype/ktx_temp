package com.darktype.tmoneyauto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NasReporter {
    private const val TAG = "NasReporter"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun report(nasUrl: String, result: String, message: String, points: Int = 0): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = nasUrl.trimEnd('/') + "/api/tmoney/report"
                val json = JSONObject().apply {
                    put("status", if (result == "checked_in" || result == "already_done") "success" else "error")
                    put("result", result)
                    put("message", message)
                    put("points", points)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(body)
                    .build()

                Log.d(TAG, "Sending report to $cleanUrl: $json")
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "Report response: code=${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send report to NAS: ${e.message}", e)
                false
            }
        }
    }
}
