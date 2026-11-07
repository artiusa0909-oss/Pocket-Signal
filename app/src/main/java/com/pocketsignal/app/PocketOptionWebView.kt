package com.pocketsignal.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val POCKET_OPTION_HOME = "https://pocketoption.com/en/"

private fun isPocketOptionHost(host: String?): Boolean {
    if (host == null) return false
    return host == "pocketoption.com" || host.endsWith(".pocketoption.com")
}

/**
 * Displays the Pocket Option website inside the app.
 *
 * Login is performed by the user on the broker's own HTTPS page.
 * This component deliberately does not expose cookies, passwords, local
 * storage, page JavaScript objects, or session identifiers to native code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PocketOptionWebView(
    modifier: Modifier = Modifier,
    onStatus: (String) -> Unit
) {
    val webView = remember {
        WebView(androidx.compose.ui.platform.LocalContext.current).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

            CookieManager.getInstance().setAcceptCookie(true)

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    val allowed = uri.scheme == "https" && isPocketOptionHost(uri.host)
                    if (!allowed) onStatus("Переход заблокирован: адрес вне pocketoption.com")
                    return !allowed
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    onStatus("Загружается платформа Pocket Option…")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    onStatus("Страница загружена. Вход выполняется непосредственно на сайте Pocket Option.")
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError
                ) {
                    handler.cancel()
                    onStatus("Соединение остановлено: ошибка SSL-сертификата.")
                }
            }

            loadUrl(POCKET_OPTION_HOME)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}
