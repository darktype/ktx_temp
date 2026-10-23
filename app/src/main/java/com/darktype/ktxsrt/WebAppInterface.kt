package com.darktype.ktxsrt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(
    private val context: Context,
    private val prefManager: PreferenceManager,
    private val onCredentialsChanged: (() -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun saveCredentials(email: String, password: String, autoLogin: Boolean) {
        prefManager.saveCredentials(email, password, autoLogin)
        mainHandler.post {
            onCredentialsChanged?.invoke()
        }
    }

    @JavascriptInterface
    fun clearCredentials() {
        prefManager.clearCredentials()
        mainHandler.post {
            showToast("자동 로그인 정보가 초기화되었습니다.")
            onCredentialsChanged?.invoke()
        }
    }

    @JavascriptInterface
    fun getSavedCredentials(): String {
        return prefManager.toJsonString()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
