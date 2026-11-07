package com.pocketsignal.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.WebView

private val ScreenBg = Color(0xFF080D13)
private val PanelBg = Color(0xFF111B26)
private val Accent = Color(0xFF20D69A)

private const val MOBILE_BOTTOM_NAV_REPAIR_SCRIPT = """
(function() {
  try {
    var labels = [
      /^(deals|trades|сделки)$/i,
      /^(signals|сигналы)$/i,
      /^(social trading|social tra\.\.\.|социальная торговля)$/i,
      /^(express trading|express tra\.\.\.|экспресс-сделки)$/i,
      /^(tournaments|tournam\.\.\.|турниры)$/i,
      /^(pending trades|pending tr\.\.\.|отложенные сделки)$/i
    ];
    function norm(s) { return (s || '').replace(/\\s+/g, ' ').trim(); }
    var nodes = Array.from(document.querySelectorAll('body *')).filter(function(el) {
      if (el.children.length > 3) return false;
      var t = norm(el.innerText || el.textContent);
      return t.length > 0 && t.length < 50 && labels.some(function(re) { return re.test(t); });
    });
    var found = [];
    nodes.forEach(function(el) {
      var t = norm(el.innerText || el.textContent);
      var idx = labels.findIndex(function(re) { return re.test(t); });
      if (idx >= 0 && !found.some(function(x) { return x.idx === idx; })) found.push({el:el, idx:idx});
    });
    var candidates = [];
    found.forEach(function(item) {
      var p = item.el;
      for (var depth = 0; p && depth < 8 && p !== document.body; depth++, p = p.parentElement) {
        var count = found.filter(function(x) { return p.contains(x.el); }).length;
        if (count >= 4) {
          var r = p.getBoundingClientRect();
          candidates.push({el:p, count:count, area:Math.max(1, r.width * r.height), depth:depth});
        }
      }
    });
    candidates.sort(function(a,b) {
      if (b.count !== a.count) return b.count - a.count;
      if (a.area !== b.area) return a.area - b.area;
      return a.depth - b.depth;
    });
    var target = candidates.length ? candidates[0].el : null;
    if (!target) return JSON.stringify({applied:false, reason:'bottom navigation not found', labels:found.length});
    if (target.getAttribute('data-pocket-signal-bottom-nav') !== '1') {
      target.setAttribute('data-pocket-signal-bottom-nav', '1');
      target.style.setProperty('position', 'fixed', 'important');
      target.style.setProperty('left', '0', 'important');
      target.style.setProperty('right', '0', 'important');
      target.style.setProperty('top', 'auto', 'important');
      target.style.setProperty('bottom', '0', 'important');
      target.style.setProperty('width', '100vw', 'important');
      target.style.setProperty('height', '64px', 'important');
      target.style.setProperty('max-height', '64px', 'important');
      target.style.setProperty('display', 'flex', 'important');
      target.style.setProperty('flex-direction', 'row', 'important');
      target.style.setProperty('align-items', 'stretch', 'important');
      target.style.setProperty('justify-content', 'space-around', 'important');
      target.style.setProperty('overflow-x', 'auto', 'important');
      target.style.setProperty('overflow-y', 'hidden', 'important');
      target.style.setProperty('background', '#111b26', 'important');
      target.style.setProperty('border-top', '1px solid #263747', 'important');
      target.style.setProperty('z-index', '2147483646', 'important');
      Array.from(target.children).forEach(function(child) {
        child.style.setProperty('flex', '1 1 0', 'important');
        child.style.setProperty('min-width', '0', 'important');
        child.style.setProperty('height', '100%', 'important');
        child.style.setProperty('display', 'flex', 'important');
        child.style.setProperty('align-items', 'center', 'important');
        child.style.setProperty('justify-content', 'center', 'important');
      });
    }
    var main = document.querySelector('.m-main-wrap.js-m-main-wrap');
    if (main) {
      main.style.setProperty('height', 'calc(100vh - 64px)', 'important');
      main.style.setProperty('min-height', '0', 'important');
      main.style.setProperty('max-height', 'calc(100vh - 64px)', 'important');
    }
    return JSON.stringify({applied:true, labels:found.length, navWidth:Math.round(target.getBoundingClientRect().width), navHeight:Math.round(target.getBoundingClientRect().height)});
  } catch(e) {
    return JSON.stringify({applied:false, error:String(e).slice(0,120)});
  }
})()
"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 19)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 19)
        window.decorView.systemUiVisibility = 0
        setContent { PocketSignalScreen() }
    }
}

@Composable
fun PocketSignalScreen() {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val diagnosticLog = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    LaunchedEffect(webViewRef) {
        val view = webViewRef ?: return@LaunchedEffect
        while (webViewRef === view) {
            view.evaluateJavascript(MOBILE_BOTTOM_NAV_REPAIR_SCRIPT) { result ->
                val clean = result?.removeSurrounding("\"", "\"")?.take(220) ?: "нет ответа"
                if (clean.contains("\"applied\":true") && diagnosticLog.lastOrNull() != "Нижняя панель Pocket Option: $clean") {
                    diagnosticLog.add("Нижняя панель Pocket Option: $clean")
                    while (diagnosticLog.size > 40) diagnosticLog.removeAt(0)
                }
            }
            kotlinx.coroutines.delay(1800)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ScreenBg,
            surface = PanelBg,
            primary = Accent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            PocketOptionWebView(
                reloadKey = 0,
                modifier = Modifier.fillMaxSize(),
                onWebView = { webViewRef = it },
                onStatus = { message ->
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    val entry = "[$timestamp] $message"
                    if (diagnosticLog.lastOrNull() != entry) {
                        diagnosticLog.add(entry)
                        while (diagnosticLog.size > 40) diagnosticLog.removeAt(0)
                    }
                }
            )

            Surface(
                onClick = {
                    val report = diagnosticLog.joinToString("\n")
                    if (report.isBlank()) {
                        Toast.makeText(context, "Журнал пока пуст", Toast.LENGTH_SHORT).show()
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Signal diagnostics", report))
                        Toast.makeText(context, "Диагностика скопирована. Вставь её сюда в чат.", Toast.LENGTH_LONG).show()
                    }
                },
                color = Color(0xCC111B26),
                contentColor = Accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Text(
                    "КОПИРОВАТЬ",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
