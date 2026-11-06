
package com.pocketsignal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketSignalScreen()
        }
    }
}

@Composable
fun PocketSignalScreen() {
    val background = Color(0xFF101820)
    val panel = Color(0xFF1B2836)
    val accent = Color(0xFF19C37D)

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))

            Text(
                text = "POCKET SIGNAL",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "OTC MARKET ANALYSIS",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(35.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = panel
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("СТАТУС АНАЛИЗА", color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "ОЖИДАНИЕ",
                        color = accent,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ожидание подключения графика",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = panel
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("ПАРАМЕТРЫ АНАЛИЗА", color = Color.Gray)
                    Spacer(Modifier.height(15.dp))
                    Text("Таймфрейм: M1", color = Color.White)
                    Text("Активы: ALL OTC", color = Color.White)
                    Text("Стратегия: COMBINED ML", color = Color.White)
                    Text("Экспирация: АВТО", color = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Версия 1.0 • Prototype",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}
