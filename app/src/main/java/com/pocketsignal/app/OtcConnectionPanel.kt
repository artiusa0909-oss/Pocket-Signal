package com.pocketsignal.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OtcAccent = Color(0xFF20D69A)
private val OtcPanel = Color(0xFF151F2B)
private val OtcMuted = Color(0xFF8A98A8)

@Composable
fun OtcConnectionPanel(
    onCandlesLoaded: (List<Candle>) -> Unit,
    onStatusChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(SecureApiKeyStore.load(context).orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember {
        mutableStateOf(
            if (apiKey.isBlank()) "Введите ключ OTCharts. Ключ Pocket Option не требуется."
            else "Сохранённый ключ найден. Нажмите кнопку для проверки."
        )
    }
    var usageLine by remember { mutableStateOf("") }
    var chosenSymbol by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = OtcPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().then(Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ИСТОЧНИК КОТИРОВОК · OTCHARTS",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    "Ключ хранится в зашифрованном виде через Android Keystore.",
                    color = OtcMuted,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OTCharts API key") },
                    placeholder = { Text("otc_live_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy
                )
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            message = "Сначала вставьте API-ключ OTCharts."
                            return@Button
                        }
                        busy = true
                        message = "Проверяю ключ и запрашиваю каталог инструментов…"
                        scope.launch {
                            try {
                                SecureApiKeyStore.save(context, apiKey)
                                val result = withContext(Dispatchers.IO) {
                                    val client = OtcApiClient(apiKey.trim())
                                    val usage = client.getUsage()
                                    val symbols = client.getSymbols("otc")
                                    val selected = symbols.firstOrNull {
                                        it.symbol == "EURUSD_otc" && it.isOpen != false
                                    } ?: symbols.firstOrNull { it.isOpen != false }
                                        ?: symbols.firstOrNull()
                                        ?: throw OtcApiException("Каталог OTC не содержит доступных инструментов.")
                                    val candles = client.getCandles(
                                        symbol = selected.symbol,
                                        timeframeSeconds = 60,
                                        limit = 120,
                                        venue = "otc"
                                    )
                                    Triple(usage, selected, candles)
                                }
                                chosenSymbol = result.second.symbol
                                onCandlesLoaded(result.third.candles)
                                usageLine = buildString {
                                    append(result.first.planName)
                                    result.first.requestsRemaining?.let {
                                        append(" · осталось ")
                                        append(it)
                                        result.first.requestsQuota?.let { total -> append("/$total запросов") }
                                    }
                                }
                                message = buildString {
                                    append("Каталог получен. Инструмент: ")
                                    append(result.second.name)
                                    append(". Свечей M1: ")
                                    append(result.third.candles.size)
                                    if (result.third.gapRuns > 0 || result.third.missingBars > 0) {
                                        append(". ВНИМАНИЕ: обнаружены разрывы: ")
                                        append(result.third.missingBars)
                                        append(" свечей.")
                                    } else {
                                        append(". Разрывов в полученной серии не обнаружено.")
                                    }
                                    if (result.third.isOpen == false) append(" Инструмент закрыт брокером.")
                                }
                                onStatusChanged("OTCharts подключён. Получен снимок M1 для $chosenSymbol.")
                            } catch (error: Exception) {
                                message = error.message ?: "Не удалось подключиться к OTCharts."
                                onStatusChanged("Нет подтверждённых котировок: ${message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OtcAccent)
                ) {
                    Text(
                        if (busy) "ПОДКЛЮЧЕНИЕ…" else "СОХРАНИТЬ КЛЮЧ И ЗАГРУЗИТЬ M1",
                        color = Color(0xFF07130F),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (usageLine.isNotBlank()) {
                    Text(usageLine, color = OtcAccent, fontSize = 11.sp)
                }
                if (chosenSymbol.isNotBlank()) {
                    Text("Актив: $chosenSymbol", color = Color.White, fontSize = 11.sp)
                }
                Text(message, color = OtcMuted, fontSize = 11.sp)
                Text(
                    "Тест загружает исторические свечи по запросу. Бесплатный тариф OTCharts не поддерживает live-stream; автоматические сигналы пока отключены.",
                    color = Color(0xFFFFC266),
                    fontSize = 10.sp
                )
            }
        }
    }
}
