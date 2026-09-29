package com.example.webapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // Your app lives in app/src/main/assets/ and is served from this fake https origin,
    // so fetch(), localStorage, service workers etc. behave like on a real website.
    private val host = "appassets.androidplatform.net"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(host)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // Lets you inspect the page from desktop Chrome at chrome://inspect (debug builds only)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView = WebView(this)
        setContentView(webView)

        // Keep content out from under the status/navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true      // localStorage / sessionStorage
            allowFileAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            // Local pages stay in the app; external links open in the browser
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                if (request.url.host == host) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
        }

        // Back button goes back in web history, then exits
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://$host/assets/index.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
