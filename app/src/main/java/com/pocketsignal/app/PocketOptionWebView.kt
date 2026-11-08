package com.pocketsignal.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val POCKET_OPTION_HOME = "https://pocketoption.com/en/"

private fun safeDiagnosticText(raw: String, limit: Int = 180): String {
    val withoutUrls = raw.replace(Regex("https?://[^\\s]+")) { match ->
        runCatching {
            val host = android.net.Uri.parse(match.value).host
            if (host.isNullOrBlank()) "[url]" else "https://$host"
        }.getOrDefault("[url]")
    }
    return withoutUrls
        .replace(Regex("(?i)(token|authorization|cookie|session|password|api[_-]?key)(\\s*[:=]\\s*)[^\\s,;]+")) { match -> match.groupValues[1] + "=[redacted]" }
        .take(limit)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PocketOptionWebView(
    reloadKey: Int,
    modifier: Modifier = Modifier,
    onWebView: (WebView?) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val currentOnStatus = rememberUpdatedState(onStatus)
    val currentOnWebView = rememberUpdatedState(onWebView)
    val reportStatus: (String) -> Unit = { message -> currentOnStatus.value.invoke(message) }

    key(reloadKey) {
        val webView = remember(context, reloadKey) {
            WebView(context).apply {
                val primaryWebView = this
                setBackgroundColor(android.graphics.Color.rgb(8, 13, 19))
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(false)
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mediaPlaybackRequiresUserGesture = false
                    builtInZoomControls = false
                    displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        if (newProgress < 100) {
                            reportStatus("Загрузка торговой платформы: " + newProgress + "%")
                        } else {
                            reportStatus("Страница загружена. Диагностируем модуль графика…")
                        }
                    }

                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        val text = message.message()
                        if (text.startsWith("PS_NET:")) {
                            reportStatus("СЕТЕВАЯ ДИАГНОСТИКА: " + safeDiagnosticText(text.removePrefix("PS_NET:").trim(), 260))
                        } else if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            val sourceHost = runCatching {
                                android.net.Uri.parse(message.sourceId()).host
                            }.getOrNull() ?: "скрипт"
                            val detail = safeDiagnosticText(text)
                            reportStatus("JavaScript ERROR [$sourceHost:\${message.lineNumber()}]: $detail")
                        }
                        return true
                    }

                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message
                    ): Boolean {
                        // Match the previously working embedded Pocket Option WebView behavior.
                        // The broker's normal in-page navigation remains in the primary WebView.
                        reportStatus("Отдельное окно сайта не открывается; продолжаем в основном окне.")
                        return false
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val uri = request.url
                        // Restore the known-working Pocket Option host policy from builds #90-115.
                        val allowed = uri.scheme.equals("https", ignoreCase = true) &&
                            (uri.host == "pocketoption.com" || uri.host?.endsWith(".pocketoption.com") == true)
                        if (!allowed) {
                            reportStatus("Заблокирован переход на " + (uri.host ?: "неизвестный адрес") + ".")
                        }
                        return !allowed
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        reportStatus("Открывается Pocket Option…")
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        reportStatus("Страница отображается. Ожидаем загрузку графика.")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        reportStatus("Страница загружена. Не вмешиваемся в отрисовку графика.")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        val host = request.url.host ?: "неизвестный ресурс"
                        if (request.isForMainFrame) {
                            reportStatus("Ошибка основной страницы: код ${error.errorCode}, $host.")
                        } else {
                            reportStatus("Не загрузился ресурс графика/страницы: код ${error.errorCode}, $host.")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: android.webkit.WebResourceResponse
                    ) {
                        val host = request.url.host ?: "неизвестный ресурс"
                        if (errorResponse.statusCode >= 400) {
                            reportStatus("HTTP ${errorResponse.statusCode}: $host" +
                                if (request.isForMainFrame) " (основная страница)." else " (ресурс страницы/графика).")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: SslError
                    ) {
                        handler.cancel()
                        reportStatus("Соединение остановлено: ошибка SSL-сертификата.")
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: android.webkit.RenderProcessGoneDetail
                    ): Boolean {
                        reportStatus("Процесс отображения страницы остановился. Нажми «Обновить».")
                        return true
                    }
                }

                // Keep Pocket Option's own JavaScript, WebSocket and chart rendering untouched.

                loadUrl(POCKET_OPTION_HOME)
            }
        }

        DisposableEffect(webView) {
            currentOnWebView.value.invoke(webView)
            onDispose {
                currentOnWebView.value.invoke(null)
                webView.stopLoading()
                webView.destroy()
            }
        }

        AndroidView(factory = { webView }, modifier = modifier)
    }
}
