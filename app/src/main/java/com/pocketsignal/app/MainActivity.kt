package com.pocketsignal.app

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
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

private data class DemoCandle(
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float
)

private val demoCandles = listOf(
    DemoCandle(1.0820f, 1.0830f, 1.0815f, 1.0827f),
    DemoCandle(1.0827f, 1.0832f, 1.0821f, 1.0823f),
    DemoCandle(1.0823f, 1.0828f, 1.0818f, 1.0820f),
    DemoCandle(1.0820f, 1.0825f, 1.0812f, 1.0815f),
    DemoCandle(1.0815f, 1.0822f, 1.0810f, 1.0820f),
    DemoCandle(1.0820f, 1.0831f, 1.0818f, 1.0828f),
    DemoCandle(1.0828f, 1.0835f, 1.0824f, 1.0832f),
    DemoCandle(1.0832f, 1.0837f, 1.0826f, 1.0829f),
    DemoCandle(1.0829f, 1.0833f, 1.0820f, 1.0822f),
    DemoCandle(1.0822f, 1.0827f, 1.0817f, 1.0825f),
    DemoCandle(1.0825f, 1.0834f, 1.0823f, 1.0831f),
    DemoCandle(1.0831f, 1.0838f, 1.0828f, 1.0836f),
    DemoCandle(1.0836f, 1.0840f, 1.0829f, 1.0830f),
    DemoCandle(1.0830f, 1.0834f, 1.0822f, 1.0825f),
    DemoCandle(1.0825f, 1.0830f, 1.0819f, 1.0821f),
    DemoCandle(1.0821f, 1.0828f, 1.0818f, 1.0826f),
    DemoCandle(1.0826f, 1.0836f, 1.0824f, 1.0834f),
    DemoCandle(1.0834f, 1.0841f, 1.0830f, 1.0838f),
    DemoCandle(1.0838f, 1.0842f, 1.0831f, 1.0833f),
    DemoCandle(1.0833f, 1.0837f, 1.0827f, 1.0830f),
    DemoCandle(1.0830f, 1.0839f, 1.0828f, 1.0837f),
    DemoCandle(1.0837f, 1.0845f, 1.0834f, 1.0842f),
    DemoCandle(1.0842f, 1.0847f, 1.0836f, 1.0839f),
    DemoCandle(1.0839f, 1.0844f, 1.0835f, 1.0841f)
)

@Composable
fun PocketSignalScreen() {
    val context = LocalContext.current
    val liveSignal by produceState(
        initialValue = Triple(ScreenCaptureState.signalText, ScreenCaptureState.upProbability, ScreenCaptureState.downProbability)
    ) {
        while (true) {
            value = Triple(ScreenCaptureState.signalText, ScreenCaptureState.upProbability, ScreenCaptureState.downProbability)
            delay(500L)
        }
    }
    var selectedAsset by remember { mutableStateOf("EUR/USD OTC") }
    var assetMenuExpanded by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf("Ожидание подключения к источнику котировок")
    }
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val captureIntent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(captureIntent)
            } else {
                context.startService(captureIntent)
            }
            statusMessage = "Захват запущен. Выбери окно Pocket Option в системном окне Android."
        } else {
            statusMessage = "Подключение отменено. Котировки не получены."
        }
    }

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
                        candles = demoCandles,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                    Text(
                        "ДЕМО-ДАННЫЕ • НЕ ЯВЛЯЮТСЯ КОТИРОВКАМИ РЫНКА",
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
                    Text(liveSignal.first, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("ВВЕРХ — ${liveSignal.second}%", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("ВНИЗ — ${liveSignal.third}%", color = Color(0xFFFF647C), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Проценты показывают относительную поддержку факторов на основе распознанных свечей, а не статистическую вероятность выигрыша.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = {
                    captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("ПОДКЛЮЧИТЬ POCKET OPTION", color = Color(0xFF07130F), fontWeight = FontWeight.Bold)
            }

            Text(
                "Захват выбранного окна обрабатывается локально на телефоне. Снимки экрана не отправляются на сервер.",
                color = Muted,
                fontSize = 10.sp
            )

            Button(
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                        statusMessage = "Разреши отображение поверх других приложений, затем нажми кнопку ещё раз."
                    } else {
                        context.startService(Intent(context, FloatingSignalService::class.java))
                        statusMessage = "Плавающая панель запущена. Реальные котировки пока не подключены."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("ЗАПУСТИТЬ ПЛАВАЮЩЕЕ ОКНО", color = Color(0xFF07130F), fontWeight = FontWeight.Bold)
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
    candles: List<DemoCandle>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (candles.isEmpty()) return@Canvas

        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val range = (maxPrice - minPrice).coerceAtLeast(0.0001f)
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
            fun y(price: Float): Float =
                chartPadding + (maxPrice - price) / range * chartHeight

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
