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

private fun isPocketOptionHost(host: String?): Boolean =
    host == "pocketoption.com" || host?.endsWith(".pocketoption.com") == true

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
                            reportStatus("Страница загружена. Проверяем отображение терминала…")
                        }
                    }

                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        // Console text may contain page data; do not forward it to logs or UI.
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val uri = request.url
                        val allowed = uri.scheme == "https" && isPocketOptionHost(uri.host)
                        if (!allowed) {
                            reportStatus(
                                "Заблокирован переход на " +
                                    (uri.host ?: "неизвестный адрес") +
                                    ". Проверим домен перед добавлением."
                            )
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
                        reportStatus("Загрузка страницы завершена. Если график пустой, нажми «Обновить».")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) {
                            reportStatus(
                                "Ошибка загрузки страницы (" +
                                    error.errorCode +
                                    "). Проверь соединение и WebView."
                            )
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: android.webkit.WebResourceResponse
                    ) {
                        if (request.isForMainFrame) {
                            reportStatus("Сайт вернул HTTP " + errorResponse.statusCode + ".")
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
