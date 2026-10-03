package com.example.webapp

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class WebAppInterface(
    private val activity: ComponentActivity,
    private val webView: WebView
) {
    private val dao = AppDatabase.get(activity).userDao()
    private val prefs = activity.getSharedPreferences("session", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun register(callId: String, username: String, password: String) {
        activity.lifecycleScope.launch {
            sendResult(callId, AuthRepository.register(dao, username, password))
        }
    }

    @JavascriptInterface
    fun login(callId: String, username: String, password: String) {
        activity.lifecycleScope.launch {
            val normalizedUsername = AuthRepository.normalizeUsername(username)
            val success = AuthRepository.login(dao, username, password)
            if (success) prefs.edit().putString("logged_in_username", normalizedUsername).apply()
            sendResult(callId, success)
        }
    }

    @JavascriptInterface
    fun isLoggedIn(): Boolean = prefs.getString("logged_in_username", null) != null

    @JavascriptInterface
    fun getUsername(): String = prefs.getString("logged_in_username", "") ?: ""

    @JavascriptInterface
    fun logout() {
        prefs.edit().remove("logged_in_username").apply()
    }

    private fun sendResult(callId: String, success: Boolean) {
        if (!callId.matches(Regex("[0-9]{1,10}"))) return
        webView.post {
            webView.evaluateJavascript("window.onNativeResult($callId, $success)", null)
        }
    }
}