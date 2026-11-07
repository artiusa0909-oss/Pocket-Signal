package com.pocketsignal.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class OtcSymbol(
    val symbol: String,
    val name: String,
    val isOpen: Boolean?
)

data class OtcUsage(
    val planName: String,
    val requestsUsed: Int?,
    val requestsRemaining: Int?,
    val requestsQuota: Int?,
    val streamsOpen: Int?,
    val streamsLimit: Int?
)

data class OtcCandleBatch(
    val candles: List<Candle>,
    val gapRuns: Int,
    val missingBars: Int,
    val isOpen: Boolean?
)

class OtcApiException(message: String) : Exception(message)

/**
 * Read-only OTCharts adapter. It never accesses Pocket Option credentials
 * and cannot submit or execute trades.
 */
class OtcApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://otcharts.com"
) {
    init {
        require(apiKey.isNotBlank()) { "Введите API-ключ OTCharts." }
    }

    fun getUsage(): OtcUsage {
        val json = JSONObject(request("/v1/usage"))
        val requests = json.optJSONObject("requests") ?: JSONObject()
        val streams = json.optJSONObject("streams") ?: JSONObject()
        return OtcUsage(
            planName = json.optString("planName", json.optString("plan", "Неизвестный тариф")),
            requestsUsed = requests.nullableInt("used"),
            requestsRemaining = requests.nullableInt("remaining"),
            requestsQuota = requests.nullableInt("quota"),
            streamsOpen = streams.nullableInt("open"),
            streamsLimit = streams.nullableInt("limit")
        )
    }

    fun getSymbols(venue: String = "otc"): List<OtcSymbol> {
        val path = "/v1/symbols?venue=" + encode(venue)
        val json = JSONObject(request(path))
        val array = json.optJSONArray("symbols")
            ?: throw OtcApiException("API вернуло ответ без списка инструментов.")
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val symbol = item.optString("symbol").trim()
                if (symbol.isNotEmpty()) {
                    add(
                        OtcSymbol(
                            symbol = symbol,
                            name = item.optString("name", symbol),
                            isOpen = item.nullableBoolean("open")
                        )
                    )
                }
            }
        }
    }

    fun getCandles(
        symbol: String,
        timeframeSeconds: Int = 60,
        limit: Int = 120,
        venue: String = "otc"
    ): OtcCandleBatch {
        require(timeframeSeconds == 60) { "Для текущей версии поддерживается только M1." }
        require(limit in 30..300) { "Количество свечей должно быть от 30 до 300." }

        val path = "/v1/candles?venue=${encode(venue)}&symbol=${encode(symbol)}&tf=$timeframeSeconds&limit=$limit"
        val json = JSONObject(request(path))
        val array = json.optJSONArray("candles")
            ?: throw OtcApiException("API вернуло ответ без массива свечей.")

        val candles = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val timeSeconds = item.optLong("time", 0L)
                val open = item.optDouble("open", Double.NaN)
                val high = item.optDouble("high", Double.NaN)
                val low = item.optDouble("low", Double.NaN)
                val close = item.optDouble("close", Double.NaN)
                if (
                    timeSeconds > 0L && open.isFinite() && high.isFinite() &&
                    low.isFinite() && close.isFinite() && open > 0.0 &&
                    high >= maxOf(open, close) && low <= minOf(open, close) && high >= low
                ) {
                    add(
                        Candle(
                            timestampMillis = timeSeconds * 1_000L,
                            open = open,
                            high = high,
                            low = low,
                            close = close,
                            volume = item.optDouble("volume", 0.0)
                        )
                    )
                }
            }
        }.sortedBy { it.timestampMillis }.distinctBy { it.timestampMillis }

        val gaps = json.optJSONObject("gaps") ?: JSONObject()
        return OtcCandleBatch(
            candles = candles,
            gapRuns = gaps.optInt("runs", 0),
            missingBars = gaps.optInt("missingBars", 0),
            isOpen = json.nullableBoolean("open")
        )
    }

    private fun request(path: String): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PocketSignal-Android/1.0")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    JSONObject(body).optString("error").takeIf { it.isNotBlank() }
                }.getOrNull()
                val message = when (status) {
                    401 -> "Ключ не принят: проверьте ключ и подтверждение email в OTCharts."
                    403 -> "Тариф не открывает Pocket Option или выбранный инструмент."
                    429 -> "Лимит запросов исчерпан. Проверьте квоту OTCharts."
                    502 -> "Источник котировок временно не отвечает."
                    else -> detail ?: "Ошибка OTCharts: HTTP $status."
                }
                throw OtcApiException(message)
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.nullableInt(name: String): Int? =
        if (isNull(name) || !has(name)) null else optInt(name)

    private fun JSONObject.nullableBoolean(name: String): Boolean? =
        if (isNull(name) || !has(name)) null else optBoolean(name)
}
