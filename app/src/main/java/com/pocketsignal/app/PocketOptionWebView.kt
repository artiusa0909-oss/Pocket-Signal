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
      let glInfo = null;
      try {
        const gl = c.getContext('webgl2') || c.getContext('webgl') || c.getContext('experimental-webgl');
        if (gl) {
          const lost = gl.isContextLost();
          let pixelStats = null;
          if (!lost && c.width > 8 && c.height > 8) {
            try {
              const pixels = new Uint8Array(8 * 8 * 4);
              const fb = gl.getParameter(gl.FRAMEBUFFER_BINDING);
              gl.readPixels(
                Math.max(0, Math.floor(c.width / 2) - 4),
                Math.max(0, Math.floor(c.height / 2) - 4),
                8, 8, gl.RGBA, gl.UNSIGNED_BYTE, pixels
              );
              const colors = new Set();
              let nonTransparent = 0;
              let nonBlack = 0;
              for (let i = 0; i < pixels.length; i += 4) {
                colors.add(pixels[i] + ',' + pixels[i+1] + ',' + pixels[i+2] + ',' + pixels[i+3]);
                if (pixels[i+3] > 0) nonTransparent++;
                if (pixels[i] > 8 || pixels[i+1] > 8 || pixels[i+2] > 8) nonBlack++;
              }
              pixelStats = {
                uniqueColors: colors.size,
                nonTransparentPixels: nonTransparent,
                nonBlackPixels: nonBlack,
                framebufferBound: !!fb
              };
            } catch (pixelError) {
              pixelStats = {error:String(pixelError).slice(0,80)};
            }
          }
          glInfo = {
            context: (gl instanceof WebGL2RenderingContext) ? 'webgl2' : 'webgl',
            lost: lost,
            drawingBuffer: gl.drawingBufferWidth + 'x' + gl.drawingBufferHeight,
            version: String(gl.getParameter(gl.VERSION) || '').slice(0,80),
            renderer: String(gl.getParameter(gl.RENDERER) || '').slice(0,100),
            pixelStats: pixelStats
          };
        } else {
          glInfo = {context:'no-webgl-context'};
        }
      } catch (glError) {
        glInfo = {error:String(glError).slice(0,100)};
      }
      return {
        bitmap: c.width + 'x' + c.height,
        rect: Math.round(r.width) + 'x' + Math.round(r.height),
        display: s.display,
        visibility: s.visibility,
        opacity: s.opacity,
        gl: glInfo,
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
    const viewportWidth = Math.max(1, document.documentElement.clientWidth || window.innerWidth || 0);
    const viewportHeight = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 0);
    if (!target) return JSON.stringify({repair:'chart-1-not-found', viewportWidth, viewportHeight});
    if (viewportWidth < 160 || viewportHeight < 250) return JSON.stringify({repair:'skipped-small-viewport', viewportWidth, viewportHeight});
    const desiredHeight = Math.max(280, Math.min(420, Math.round(viewportHeight * 0.58)));
    const styleId = 'pocket-signal-chart-layout-fix';
    let style = document.getElementById(styleId);
    if (!style) {
      style = document.createElement('style');
      style.id = styleId;
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = [
      '#chart-1{width:100vw!important;max-width:100vw!important;min-width:0!important;height:'+desiredHeight+'px!important;min-height:'+desiredHeight+'px!important;box-sizing:border-box!important;overflow:hidden!important}',
      '#chart-1 canvas{display:block!important;width:100%!important;height:100%!important;max-width:100%!important;box-sizing:border-box!important}',
      '.charts.single,.charts.single>.layout-container,.charts.single .chart-item.is-quick,.charts.single .chart{width:100vw!important;max-width:100vw!important;min-width:0!important;box-sizing:border-box!important}',
      '.charts.single>.layout-container,.charts.single .chart-item.is-quick,.charts.single .chart{height:'+desiredHeight+'px!important;min-height:'+desiredHeight+'px!important}'
    ].join('');
    const changed = [];
    let el = target;
    for (let i = 0; el && i < 5; i++, el = el.parentElement) {
      const rect = el.getBoundingClientRect();
      const s = window.getComputedStyle(el);
      if (Math.abs(rect.width - viewportWidth) > 2 || rect.height < 2) {
        changed.push({
          tag:el.tagName,
          id:(el.id||'').slice(0,40),
          className:(typeof el.className==='string'?el.className:'').slice(0,60),
          before:Math.round(rect.width)+'x'+Math.round(rect.height),
          computedWidth:s.width
        });
      }
    }
    target.style.setProperty('width','100vw','important');
    target.style.setProperty('max-width','100vw','important');
    target.style.setProperty('height',desiredHeight+'px','important');
    target.style.setProperty('min-height',desiredHeight+'px','important');
    target.style.setProperty('box-sizing','border-box','important');
    const canvasList = Array.from(target.querySelectorAll('canvas'));
    canvasList.forEach(function(c) {
      c.style.setProperty('width','100%','important');
      c.style.setProperty('height','100%','important');
      c.style.setProperty('max-width','100%','important');
      c.style.setProperty('display','block','important');
    });
    requestAnimationFrame(function() {
      window.dispatchEvent(new Event('resize'));
      if (window.visualViewport) {
        window.visualViewport.dispatchEvent(new Event('resize'));
      }
    });
    return JSON.stringify({
      repair:'responsive-width-height-applied',
      viewportWidth,
      height:desiredHeight,
      canvasCount:canvasList.length,
      changed
    });
  } catch (e) {
    return JSON.stringify({repair:'error',detail:String(e).slice(0,120)});
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
