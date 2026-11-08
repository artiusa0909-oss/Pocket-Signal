package com.pocketsignal.app

import android.os.Bundle
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var signalDecision by remember { mutableStateOf<SignalDecision?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var pageVisible by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("Подключаем торговую платформу…") }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            PocketOptionWebView(
                reloadKey = reloadKey,
                modifier = Modifier.fillMaxSize(),
                onWebView = { webViewRef = it },
                onStatus = { message ->
                    loadingStatus = message
                    if (message.startsWith("Страница отображается") ||
                        message.startsWith("Страница загружена.")) {
                        pageVisible = true
                    }
                    if (message.startsWith("Ошибка основной страницы") ||
                        message.startsWith("Соединение остановлено") ||
                        message.startsWith("Процесс отображения страницы")) {
                        pageVisible = false
                    }
                }
            )

            // Analysis panel sits at the bottom, above the broker's own bottom navigation.
            SignalPanel(
                decision = signalDecision,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 76.dp)
            )

            if (!pageVisible) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBg.copy(alpha = 0.94f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Accent)
                        Text(
                            text = loadingStatus,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Button(
                            onClick = {
                                pageVisible = false
                                loadingStatus = "Повторное подключение…"
                                reloadKey++
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Обновить")
                        }
                    }
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
        hasSignal -> "КАНДИДАТ СИГНАЛА"
        decision == null -> "СБОР ДАННЫХ"
        else -> "НЕТ СИГНАЛА"
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
        modifier = modifier.widthIn(max = 250.dp),
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
                Text(if (hasSignal) "БАЛЛ ${decision?.confidence}/100" else "—",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(detail, color = Muted, fontSize = 8.sp, maxLines = 1)
        }
    }
}
