package com.pocketsignal.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScreenBg = Color(0xFF080D13)
private val PanelBg = Color(0xFF111B26)
private val Accent = Color(0xFF20D69A)
private val Muted = Color(0xFF91A0B1)
private val Warning = Color(0xFFFFC266)
private val SignalUp = Color(0xFF20D69A)
private val SignalDown = Color(0xFFFF647C)

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
    var reloadKey by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var signalDecision by remember { mutableStateOf<SignalDecision?>(null) }
    val diagnosticLog = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ScreenBg,
            surface = PanelBg,
            primary = Accent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("POCKET SIGNAL", color = Color.White, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    Text("MARKET MONITOR  •  M1", color = Muted, fontSize = 9.sp,
                        letterSpacing = 1.2.sp)
                }
                Surface(
                    onClick = { reloadKey++ },
                    color = PanelBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ОБНОВИТЬ", color = Accent, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }

            Surface(
                color = PanelBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
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
                        color = Color(0xFF173A35),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "СКОПИРОВАТЬ",
                            color = Accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PocketOptionWebView(
                    reloadKey = reloadKey,
                    modifier = Modifier.fillMaxSize(),
                    onWebView = { webViewRef = it },
                    onStatus = { message ->
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        val entry = "[$timestamp] $message"
                        if (diagnosticLog.lastOrNull() != entry) {
                            diagnosticLog.add(entry)
                            while (diagnosticLog.size > 40) diagnosticLog.removeAt(0)
                        }
                    }
                )

                SignalPanel(
                    decision = signalDecision,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )
            }

            Surface(color = PanelBg, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("АНАЛИЗ: ОЖИДАНИЕ ПРОВЕРЕННЫХ КОТИРОВОК",
                        color = Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("Направление появится после проверки цены и времени рыночных данных.",
                        color = Muted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SignalPanel(
    decision: SignalDecision?,
    modifier: Modifier = Modifier
) {
    val hasSignal = decision?.ready == true && decision.direction != null
    val directionColor = when (decision?.direction) {
        SignalDirection.CALL -> SignalUp
        SignalDirection.PUT -> SignalDown
        null -> Muted
    }
    val status = when {
        hasSignal -> "СИГНАЛ ПОДТВЕРЖДЁН"
        decision == null -> "СБОР ДАННЫХ"
        else -> "НЕТ УВЕРЕННОГО СИГНАЛА"
    }
    val direction = when {
        !hasSignal -> "—"
        decision?.direction == SignalDirection.CALL -> "↑ ВВЕРХ"
        else -> "↓ ВНИЗ"
    }
    val detail = when {
        hasSignal -> decision?.reasons?.take(2)?.joinToString(" · ") ?: "Технические факторы совпали"
        decision == null -> "Ожидаем проверенные котировки"
        else -> decision.message
    }

    Surface(
        modifier = modifier,
        color = PanelBg.copy(alpha = 0.96f),
        shape = RoundedCornerShape(9.dp),
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("АНАЛИЗ M1", color = Color.White, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Text(status, color = if (hasSignal) directionColor else Warning,
                    fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(direction, color = directionColor, fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text(if (hasSignal) "${decision?.confidence}%" else "—",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(detail, color = Muted, fontSize = 8.sp, maxLines = 1)
        }
    }
}
