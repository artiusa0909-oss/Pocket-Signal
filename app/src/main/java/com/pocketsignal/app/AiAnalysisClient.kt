package com.pocketsignal.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Pocket Signal server-side GPT gateway.
 *
 * OpenAI credentials must remain on the server. This Android client sends
 * normalized, completed OHLC candles only; never account/session information.
 */
data class TimeframeCandles(
    val timeframe: String,
    val candles: List<Candle>
)

data class AiAnalysisRequest(
    val symbol: String,
    val marketType: String,
    val mode: String,
    val series: List<TimeframeCandles>
)

data class AiAnalysisResult(
    val direction: String,
    val score: Int,
    val expiryMinutes: Int,
    val reasons: List<String>,
    val riskFlags: List<String>,
    val asOf: String,
    val model: String
)

class AiAnalysisClient(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 15_000
) {
    suspend fun analyze(request: AiAnalysisRequest): AiAnalysisResult =
        withContext(Dispatchers.IO) {
            require(baseUrl.startsWith("https://")) {
                "Для GPT API требуется настроенный HTTPS endpoint."
            }
            require(request.symbol.isNotBlank())
            require(request.mode == "balanced" || request.mode == "strict")
            require(request.series.isNotEmpty())
            request.series.forEach { series ->
                require(series.candles.size >= 35) {
                    "Недостаточно свечей для ${series.timeframe}: минимум 35."
                }
                require(series.candles.zipWithNext().all { (a, b) ->
                    b.timestampMillis > a.timestampMillis
                }) { "Свечи ${series.timeframe} должны идти по возрастанию времени." }
            }

            val connection = (URL(baseUrl.trimEnd('/') + "/v1/analyze").openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val body = request.toJson().toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw IllegalStateException("GPT gateway вернул HTTP $status.")
                }
                parseResult(JSONObject(responseText))
            } finally {
                connection.disconnect()
            }
        }

    private fun AiAnalysisRequest.toJson(): JSONObject = JSONObject().apply {
        put("symbol", symbol)
        put("market_type", marketType)
        put("mode", mode)
        put("timeframes", JSONArray().apply {
            series.forEach { item ->
                put(JSONObject().apply {
                    put("timeframe", item.timeframe)
                    put("candles", JSONArray().apply {
                        item.candles.forEach { candle ->
                            put(JSONObject().apply {
                                put("timestamp_ms", candle.timestampMillis)
                                put("open", candle.open)
                                put("high", candle.high)
                                put("low", candle.low)
                                put("close", candle.close)
                                put("volume", candle.volume)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun parseResult(json: JSONObject): AiAnalysisResult {
        val direction = json.optString("direction", "WAIT")
        require(direction in setOf("UP", "DOWN", "WAIT")) {
            "GPT gateway вернул неизвестное направление."
        }
        val score = json.optInt("score", -1)
        require(score in 0..100) { "Некорректная оценка GPT." }
        val expiry = json.optInt("expiry_minutes", 0)
        require(expiry in 1..60) { "Некорректная экспирация GPT." }
        return AiAnalysisResult(
            direction = direction,
            score = score,
            expiryMinutes = expiry,
            reasons = json.optStringArray("reasons"),
            riskFlags = json.optStringArray("risk_flags"),
            asOf = json.optString("as_of"),
            model = json.optString("model")
        )
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() && it != "null" }
        }
    }
}
