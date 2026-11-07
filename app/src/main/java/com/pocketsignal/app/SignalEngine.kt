package com.pocketsignal.app

import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic technical-analysis baseline for the future combined_ml pipeline.
 * This is NOT a trained ML model. Do not enable live signals before a verified
 * data adapter is connected and the strategy has been back-tested.
 */
class SignalEngine(
    private val minimumConfidence: Int = 72,
    private val minimumCandles: Int = 30
) {
    fun analyze(input: List<Candle>): SignalDecision {
        val candles = input.filter { c ->
            c.timestampMillis > 0L && c.open > 0.0 && c.high > 0.0 && c.low > 0.0 && c.close > 0.0 &&
                c.high >= max(c.open, c.close) && c.low <= minOf(c.open, c.close) && c.high >= c.low
        }.sortedBy { it.timestampMillis }.distinctBy { it.timestampMillis }

        if (candles.size < minimumCandles) {
            return noSignal("Недостаточно свечей: требуется минимум $minimumCandles, получено ${candles.size}.")
        }

        val closes = candles.map { it.close }
        val fast = ema(closes, 9)
        val slow = ema(closes, 21)
        val rsiValue = rsi(closes, 14)
        val atrValue = atr(candles, 14)
        val last = candles.last()
        val previous = candles[candles.lastIndex - 1]

        if (atrValue <= 0.0 || !atrValue.isFinite() || !rsiValue.isFinite()) {
            return noSignal("Недостаточная волатильность или некорректные данные.")
        }
        val atrRatio = atrValue / last.close
        if (atrRatio < 0.00002 || atrRatio > 0.01) {
            return noSignal("Волатильность вне рабочего диапазона стратегии.")
        }

        val up = fast > slow
        val down = fast < slow
        val momentumUp = last.close > previous.close && previous.close > candles[candles.lastIndex - 2].close
        val momentumDown = last.close < previous.close && previous.close < candles[candles.lastIndex - 2].close
        val strongTrend = abs(fast - slow) / atrValue >= 0.15
        val bullishBody = last.close > last.open && (last.close - last.open) / atrValue >= 0.20
        val bearishBody = last.close < last.open && (last.open - last.close) / atrValue >= 0.20

        var callScore = 0
        var putScore = 0
        val callReasons = mutableListOf<String>()
        val putReasons = mutableListOf<String>()
        if (up) { callScore += 22; callReasons += "EMA 9 выше EMA 21" }
        if (down) { putScore += 22; putReasons += "EMA 9 ниже EMA 21" }
        if (last.close > fast) { callScore += 12; callReasons += "Цена выше EMA 9" }
        if (last.close < fast) { putScore += 12; putReasons += "Цена ниже EMA 9" }
        if (rsiValue in 52.0..68.0) { callScore += 18; callReasons += "RSI подтверждает восходящий импульс" }
        else if (rsiValue in 32.0..48.0) { putScore += 18; putReasons += "RSI подтверждает нисходящий импульс" }
        if (momentumUp) { callScore += 14; callReasons += "Положительный импульс последних свечей" }
        if (momentumDown) { putScore += 14; putReasons += "Отрицательный импульс последних свечей" }
        if (bullishBody) { callScore += 10; callReasons += "Выраженная бычья свеча" }
        if (bearishBody) { putScore += 10; putReasons += "Выраженная медвежья свеча" }
        if (strongTrend && up) { callScore += 12; callReasons += "Достаточная сила тренда относительно ATR" }
        if (strongTrend && down) { putScore += 12; putReasons += "Достаточная сила тренда относительно ATR" }

        val best = max(callScore, putScore)
        if (best < minimumConfidence || abs(callScore - putScore) < 18) {
            return SignalDecision(null, best, 1, listOf("Фильтр SELECTIVE: недостаточно сильное или однозначное совпадение факторов."), false, "Сильного сигнала нет — пропуск сделки.")
        }
        val direction = if (callScore > putScore) SignalDirection.CALL else SignalDirection.PUT
        val reasons = if (direction == SignalDirection.CALL) callReasons else putReasons
        val trendStrength = abs(fast - slow) / atrValue
        val expiration = when { best >= 86 && trendStrength >= 0.8 -> 3; best >= 80 && trendStrength >= 0.5 -> 2; else -> 1 }
        return SignalDecision(direction, best.coerceAtMost(95), expiration, reasons, true, "Кандидат сигнала рассчитан. Требуется проверка на исторических данных.")
    }

    private fun noSignal(message: String) = SignalDecision(null, 0, 1, emptyList(), false, message)
    private fun ema(values: List<Double>, period: Int): Double {
        val alpha = 2.0 / (period + 1.0)
        return values.drop(1).fold(values.first()) { previous, value -> alpha * value + (1.0 - alpha) * previous }
    }
    private fun rsi(values: List<Double>, period: Int): Double {
        var gains = 0.0; var losses = 0.0
        for (i in values.size - period until values.size) {
            val change = values[i] - values[i - 1]
            if (change > 0.0) gains += change else losses -= change
        }
        val averageGain = gains / period; val averageLoss = losses / period
        if (averageLoss == 0.0) return if (averageGain == 0.0) 50.0 else 100.0
        val rs = averageGain / averageLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }
    private fun atr(candles: List<Candle>, period: Int): Double {
        val ranges = (1 until candles.size).map { i ->
            val c = candles[i]; val previousClose = candles[i - 1].close
            max(c.high - c.low, max(abs(c.high - previousClose), abs(c.low - previousClose)))
        }
        return ranges.takeLast(period).average()
    }
}
