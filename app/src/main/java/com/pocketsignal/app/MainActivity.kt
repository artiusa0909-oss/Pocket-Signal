package com.pocketsignal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    var selectedAsset by remember { mutableStateOf("EUR/USD OTC") }
    var assetMenuExpanded by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf("Ожидание подключения к источнику котировок")
    }
    var chartCandles by remember { mutableStateOf(emptyList<Candle>()) }

    val assets = listOf(
        "EUR/USD OTC", "GBP/USD OTC", "USD/JPY OTC",
        "AUD/USD OTC", "EUR/GBP OTC", "USD/CHF OTC"
    )

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    "POCKET SIGNAL",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "АНАЛИТИЧЕСКАЯ ПЛАТФОРМА",
                    color = Muted,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
            }

            StatusCard(statusMessage)

            OtcConnectionPanel(
                onCandlesLoaded = { chartCandles = it },
                onStatusChanged = { statusMessage = it }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("РЫНОК И ИНСТРУМЕНТ", color = Muted, fontSize = 11.sp)
                Box {
                    OutlinedButton(
                        onClick = { assetMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(selectedAsset, modifier = Modifier.weight(1f))
                        Text("⌄", color = Accent, fontSize = 20.sp)
                    }
                    DropdownMenu(
                        expanded = assetMenuExpanded,
                        onDismissRequest = { assetMenuExpanded = false }
                    ) {
                        assets.forEach { asset ->
                            DropdownMenuItem(
                                text = { Text(asset) },
                                onClick = {
                                    selectedAsset = asset
                                    assetMenuExpanded = false
                                    statusMessage = "Выбран $asset. Ожидание реальных котировок."
                                }
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ГРАФИК", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("M1", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    DemoCandlestickChart(
                        candles = chartCandles,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                    Text(
                        if (chartCandles.isEmpty()) "НЕТ ДАННЫХ • ПОДКЛЮЧИТЕ OTCHARTS" else "OTCHARTS • СВЕЧИ M1",
                        color = Color(0xFFFFC266),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("ПАРАМЕТРЫ АНАЛИЗА", color = Muted, fontSize = 11.sp)
                    ParameterRow("Таймфрейм", "M1")
                    ParameterRow("Список активов", "ALL OTC")
                    ParameterRow("Стратегия", "COMBINED ML")
                    ParameterRow("Экспирация", "АВТО · от 1 минуты")
                    ParameterRow("Фильтр сигналов", "SELECTIVE")
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ПОСЛЕДНИЙ СИГНАЛ", color = Muted, fontSize = 11.sp)
                    Text("НЕТ СИГНАЛА", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Сигналы не формируются до подключения проверенного потока котировок.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            Text(
                "Pocket Signal • Prototype 1.1",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172B2B)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("СТАТУС СИСТЕМЫ", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("ОЖИДАНИЕ ДАННЫХ", color = Accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ParameterRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DemoCandlestickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (candles.isEmpty()) return@Canvas

        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val range = (maxPrice - minPrice).coerceAtLeast(0.0000000001)
        val chartPadding = 8.dp.toPx()
        val chartHeight = size.height - chartPadding * 2
        val candleSlot = size.width / candles.size
        val bodyWidth = candleSlot * 0.56f

        for (line in 0..4) {
            val y = chartPadding + chartHeight * line / 4f
            drawLine(
                color = Color(0xFF293746),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        candles.forEachIndexed { index, candle ->
            val centerX = candleSlot * index + candleSlot / 2f
            fun y(price: Double): Float =
                chartPadding + (((maxPrice - price) / range) * chartHeight).toFloat()

            val rising = candle.close >= candle.open
            val candleColor = if (rising) Accent else Color(0xFFFF647C)
            drawLine(
                color = candleColor,
                start = Offset(centerX, y(candle.high)),
                end = Offset(centerX, y(candle.low)),
                strokeWidth = 1.5.dp.toPx()
            )

            val top = minOf(y(candle.open), y(candle.close))
            val bottom = maxOf(y(candle.open), y(candle.close))
            drawRect(
                color = candleColor,
                topLeft = Offset(centerX - bodyWidth / 2f, top),
                size = Size(bodyWidth, (bottom - top).coerceAtLeast(2.dp.toPx()))
            )
        }
    }
}
