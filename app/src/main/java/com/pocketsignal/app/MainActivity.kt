package com.pocketsignal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFF090F16)
private val PanelBg = Color(0xEE14202B)
private val Accent = Color(0xFF20D69A)
private val Muted = Color(0xFF9AA8B7)
private val Warning = Color(0xFFFFC266)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { PocketOptionIntegratedScreen() }
    }
}

@Composable
private fun PocketOptionIntegratedScreen() {
    val platformStatus = remember {
        mutableStateOf("Подключение к Pocket Option…")
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
        ) {
            // The broker page remains the primary screen. Native analysis is a
            // compact overlay; it never replaces or imitates the broker chart.
            PocketOptionWebView(
                modifier = Modifier.fillMaxSize(),
                onStatus = { platformStatus.value = it }
            )

            AnalysisStrip(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                status = platformStatus.value
            )
        }
    }
}

@Composable
private fun AnalysisStrip(
    modifier: Modifier = Modifier,
    status: String
) {
    Surface(
        modifier = modifier,
        color = PanelBg,
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "АНАЛИЗ",
                color = Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text("ALG", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text("ML", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Surface(color = Color(0xFF49391F), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "ОЖИДАНИЕ КОТИРОВОК",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Warning,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
