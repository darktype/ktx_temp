package com.darktype.ktxsrt

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "ktxsrt_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for devices where Keystore has issues
        context.getSharedPreferences("ktxsrt_fallback_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_EMAIL = "saved_email"
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_AUTO_LOGIN = "auto_login_enabled"
        private const val KEY_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "http://darktype.synology.me:10111"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var isAutoLoginEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOGIN, value).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    fun saveCredentials(email: String, pw: String, autoLogin: Boolean) {
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PASSWORD, pw)
            .putBoolean(KEY_AUTO_LOGIN, autoLogin)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .putBoolean(KEY_AUTO_LOGIN, false)
            .apply()
    }

    fun hasCredentials(): Boolean {
        return email.isNotEmpty() && password.isNotEmpty()
    }

    fun toJsonString(): String {
        return JSONObject().apply {
            put("email", email)
            put("password", password)
            put("autoLogin", isAutoLoginEnabled)
            put("hasCredentials", hasCredentials())
        }.toString()
    }
}
