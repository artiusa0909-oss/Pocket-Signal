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

private const val CHART_SNAPSHOT_SCRIPT = """
(function() {
  try {
    function box(el) {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const s = window.getComputedStyle(el);
      return {
        rect: Math.round(r.width) + 'x' + Math.round(r.height),
        client: el.clientWidth + 'x' + el.clientHeight,
        scroll: el.scrollWidth + 'x' + el.scrollHeight,
        cssHeight: s.height,
        display: s.display,
        visibility: s.visibility,
        position: s.position,
        overflow: s.overflow
      };
    }
    const canvases = Array.from(document.querySelectorAll('canvas')).slice(0, 6).map(function(c) {
      const chain = [];
      let p = c.parentElement;
      for (let i = 0; p && i < 4; i++, p = p.parentElement) {
        const r = p.getBoundingClientRect();
        chain.push({
          tag: p.tagName,
          id: (p.id || '').slice(0, 50),
          className: (typeof p.className === 'string' ? p.className : '').slice(0, 80),
          rect: Math.round(r.width) + 'x' + Math.round(r.height)
        });
      }
      const r = c.getBoundingClientRect();
      const s = window.getComputedStyle(c);
      return {
        bitmap: c.width + 'x' + c.height,
        rect: Math.round(r.width) + 'x' + Math.round(r.height),
        display: s.display,
        visibility: s.visibility,
        opacity: s.opacity,
        parentChain: chain
      };
    });
    const frames = Array.from(document.querySelectorAll('iframe')).slice(0, 8).map(function(f) {
      const r = f.getBoundingClientRect();
      const s = window.getComputedStyle(f);
      let nested = null;
      try {
        const d = f.contentDocument;
        if (d) nested = {
          ready: d.readyState,
          body: d.body ? box(d.body) : null,
          canvases: d.querySelectorAll('canvas').length
        };
      } catch (_) {}
      let host = '';
      try { host = f.src ? new URL(f.src, location.href).host : ''; } catch (_) {}
      return {
        host: host,
        rect: Math.round(r.width) + 'x' + Math.round(r.height),
        display: s.display,
        visibility: s.visibility,
        opacity: s.opacity,
        nested: nested
      };
    });
    const vv = window.visualViewport;
    const gl = !!(window.WebGLRenderingContext || window.WebGL2RenderingContext);
    return JSON.stringify({
      ready: document.readyState,
      hidden: document.hidden,
      viewport: window.innerWidth + 'x' + window.innerHeight,
      visualViewport: vv ? Math.round(vv.width) + 'x' + Math.round(vv.height) + '@' + vv.scale : null,
      dpr: window.devicePixelRatio || 1,
      documentElement: box(document.documentElement),
      body: box(document.body),
      canvases: document.querySelectorAll('canvas').length,
      canvasInfo: canvases,
      iframes: document.querySelectorAll('iframe').length,
      frameInfo: frames,
      webglApi: gl
    });
  } catch (e) {
    return JSON.stringify({snapshotError: String(e).slice(0, 120)});
  }
})()
"""


private const val CHART_LAYOUT_REPAIR_SCRIPT = """
(function() {
  try {
    const target = document.getElementById('chart-1');
    const viewportHeight = window.innerHeight || 0;
    if (!target) return JSON.stringify({repair:'chart-1-not-found'});
    if (viewportHeight < 250) return JSON.stringify({repair:'skipped-small-viewport', viewportHeight});
    const targetRect = target.getBoundingClientRect();
    if (targetRect.width < 160) return JSON.stringify({repair:'skipped-small-chart', width:Math.round(targetRect.width)});
    const desiredHeight = Math.max(280, Math.min(420, Math.round(viewportHeight * 0.58)));
    const changed = [];
    let el = target;
    for (let i = 0; el && i < 5; i++, el = el.parentElement) {
      const rect = el.getBoundingClientRect();
      if (rect.width >= 160 && rect.height < 2) {
        el.style.setProperty('height', desiredHeight + 'px', 'important');
        el.style.setProperty('min-height', desiredHeight + 'px', 'important');
        el.style.setProperty('box-sizing', 'border-box', 'important');
        changed.push({
          tag: el.tagName,
          id: (el.id || '').slice(0, 40),
          className: (typeof el.className === 'string' ? el.className : '').slice(0, 60)
        });
      }
    }
    if (changed.length) {
      requestAnimationFrame(function() {
        window.dispatchEvent(new Event('resize'));
      });
    }
    return JSON.stringify({repair:changed.length ? 'applied' : 'not-needed', height:desiredHeight, changed});
  } catch (e) {
    return JSON.stringify({repair:'error', detail:String(e).slice(0,100)});
  }
})()
"""

private fun captureChartSnapshot(
    view: WebView,
    label: String,
    reportStatus: (String) -> Unit
) {
    view.evaluateJavascript(CHART_LAYOUT_REPAIR_SCRIPT) { repairResult ->
        val repair = repairResult?.take(500) ?: "нет ответа"
        view.postDelayed({
            view.evaluateJavascript(CHART_SNAPSHOT_SCRIPT) { result ->
                val safeResult = result?.take(3200) ?: "нет ответа"
                reportStatus("Коррекция размера графика: $repair")
                reportStatus("График $label (WebView ${view.width}x${view.height}): $safeResult")
            }
        }, 250L)
    }
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
                        if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            val sourceHost = runCatching {
                                android.net.Uri.parse(message.sourceId()).host
                            }.getOrNull() ?: "скрипт"
                            val detail = safeDiagnosticText(message.message())
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
                        reportStatus("Сайт запросил отдельное окно. Проверяем поддержку торгового терминала…")
                        return false
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
                        reportStatus("Страница загружена. Снимаем параметры графика…")
                        captureChartSnapshot(view, "сразу после загрузки", reportStatus)
                        view.postDelayed({
                            captureChartSnapshot(view, "через 1,5 секунды", reportStatus)
                        }, 1500L)
                        view.postDelayed({
                            captureChartSnapshot(view, "через 4 секунды", reportStatus)
                        }, 4000L)
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
