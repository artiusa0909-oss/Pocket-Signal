package com.pocketsignal.app

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class AiChartResult(
    val direction: String,
    val upScore: Int,
    val downScore: Int,
    val confidence: Int,
    val expiryMinutes: Int,
    val summary: String,
    val risks: String
)

/** Sends a throttled chart snapshot to the user's private OpenAI backend. API keys never live in the APK. */
object ChartAiAnalyzer {
    private const val PREFS = "pocket_signal_ai"
    private const val ENDPOINT_KEY = "backend_url"

    fun configured(context: Context): Boolean =
        endpoint(context).isNotBlank()

    fun saveEndpoint(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ENDPOINT_KEY, value.trim().trimEnd('/')).apply()
    }

    fun loadEndpoint(context: Context): String = endpoint(context)

    private fun endpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ENDPOINT_KEY, "")?.trim()?.trimEnd('/') ?: ""

    fun analyze(
        context: Context,
        bitmap: Bitmap,
        asset: String,
        candles: List<Candle>,
        localUp: Int,
        localDown: Int,
        callback: (Result<AiChartResult>) -> Unit
    ) {
        val baseUrl = endpoint(context)
        if (baseUrl.isBlank()) {
            bitmap.recycle()
            callback(Result.failure(IllegalStateException("Не задан адрес ИИ-сервера")))
            return
        }
        thread(name = "PocketSignalOpenAI") {
            var connection: HttpURLConnection? = null
            try {
                val scaled = if (bitmap.width > 1100) {
                    val targetHeight = (bitmap.height * (1100.0 / bitmap.width)).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(bitmap, 1100, targetHeight, true)
                } else bitmap
                val bytes = ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
                    out.toByteArray()
                }
                if (scaled !== bitmap) scaled.recycle()
                val imageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

                val candleArray = JSONArray()
                candles.takeLast(30).forEach { candle ->
                    candleArray.put(JSONObject().apply {
                        put("timestampMillis", candle.timestampMillis)
                        put("open", candle.open)
                        put("high", candle.high)
                        put("low", candle.low)
                        put("close", candle.close)
                    })
                }
                val body = JSONObject().apply {
                    put("asset", asset)
                    put("timeframe", "M1")
                    put("candles", candleArray)
                    put("localAnalysis", JSONObject().apply {
                        put("upScore", localUp)
                        put("downScore", localDown)
                    })
                    put("imageBase64", imageBase64)
                }.toString()

                connection = (URL("$baseUrl/analyze").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 55_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection!!.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection!!.responseCode
                val stream = if (code in 200..299) connection!!.inputStream else connection!!.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("ИИ-сервер вернул HTTP $code")
                val json = JSONObject(responseText)
                val direction = json.optString("direction", "WAIT")
                val up = json.optInt("upScore", 50).coerceIn(0, 100)
                val down = json.optInt("downScore", 100 - up).coerceIn(0, 100)
                val result = AiChartResult(
                    direction = direction,
                    upScore = up,
                    downScore = down,
                    confidence = json.optInt("confidence", 0).coerceIn(0, 100),
                    expiryMinutes = json.optInt("expiryMinutes", 1).coerceIn(1, 3),
                    summary = json.optString("summary", "ИИ не вернул пояснение"),
                    risks = json.optString("risks", "")
                )
                callback(Result.success(result))
            } catch (error: Exception) {
                callback(Result.failure(error))
            } finally {
                connection?.disconnect()
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }
}
