package com.darktype.tmoneyauto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NasReporter {
    private const val TAG = "NasReporter"

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslSocketFactory = try {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    } catch (e: Exception) {
        null
    }

    private val client: OkHttpClient = try {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
        if (sslSocketFactory != null) {
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        builder.build()
    } catch (e: Exception) {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun login(nasUrl: String, userId: String, pass: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = nasUrl.trimEnd('/') + "/api/tmoney/login"
                val json = JSONObject().apply {
                    put("username", userId)
                    put("password", pass)
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(cleanUrl)
                    .post(body)
                    .build()

                Log.d(TAG, "대시보드 로그인 요청: $cleanUrl (사용자: $userId)")
                client.newCall(request).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    Log.d(TAG, "로그인 응답 코드: ${response.code}, 내용: $respStr")
                    if (response.isSuccessful) {
                        val respJson = JSONObject(respStr)
                        Pair(respJson.optBoolean("ok", true), respJson.optString("message", "인증 완료"))
                    } else {
                        val errMsg = try {
                            JSONObject(respStr).optString("error", "로그인 실패 (HTTP ${response.code})")
                        } catch (e: Exception) {
                            "로그인 실패 (HTTP ${response.code})"
                        }
                        Pair(false, errMsg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "로그인 예외 발생: ${e.message}", e)
                Pair(false, e.message ?: "네트워크 연결 실패")
            }
        }
    }

    suspend fun report(nasUrl: String, userId: String, pass: String, result: String, message: String, points: Int = 0): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = nasUrl.trimEnd('/') + "/api/tmoney/report"
                val json = JSONObject().apply {
                    put("username", userId)
                    put("password", pass)
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

                Log.d(TAG, "출석 결과 리포트 전송: $cleanUrl (사용자: $userId): $json")
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "리포트 응답 코드: ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "리포트 전송 실패: ${e.message}", e)
                false
            }
        }
    }
}
