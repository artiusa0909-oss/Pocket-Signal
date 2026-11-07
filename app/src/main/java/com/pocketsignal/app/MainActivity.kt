package com.pocketsignal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFF0B1118)
private val PanelBg = Color(0xFF151F2B)
private val Accent = Color(0xFF20D69A)
private val Muted = Color(0xFF8A98A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketSignalScreen() }
    }
}

@Composable
fun PocketSignalScreen() {
    var platformStatus by remember {
        mutableStateOf("Ожидание загрузки платформы Pocket Option")
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "POCKET SIGNAL",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "OTC MARKET MONITOR",
                    color = Muted,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row {
                        Text(
                            "СЕССИЯ POCKET OPTION",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        platformStatus,
                        color = Accent,
                        fontSize = 11.sp
                    )
                    Text(
                        "Войди самостоятельно в отдельный аккаунт. Не отправляй пароль, cookies или SSID в чат и GitHub.",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            PocketOptionWebView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onStatus = { platformStatus = it }
            )

            Text(
                "КОТИРОВКИ: ЕЩЁ НЕ ПОДКЛЮЧЕНЫ  •  СИГНАЛЫ: ОТКЛЮЧЕНЫ",
                color = Color(0xFFFFC266),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }
}
