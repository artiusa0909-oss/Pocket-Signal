package com.pocketsignal.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Selective technical-analysis engine for visible M1 candles.
 * Confidence is a confluence score, NOT a calibrated win probability.
 */
class SignalEngine(
    private val minimumConfidence: Int = 68,
    private val minimumCandles: Int = 3
) {
    fun analyze(input: List<Candle>): SignalDecision {
        val candles = input.filter { c ->
            c.timestampMillis > 0L && c.open > 0.0 && c.high > 0.0 && c.low > 0.0 && c.close > 0.0 &&
                c.high >= max(c.open, c.close) && c.low <= min(c.open, c.close) && c.high >= c.low &&
                listOf(c.open, c.high, c.low, c.close).all { it.isFinite() }
        }.sortedBy { it.timestampMillis }.distinctBy { it.timestampMillis }

        if (candles.size < minimumCandles) {
            return noSignal("Нужно минимум $minimumCandles корректных свечей M1, сейчас ${candles.size}.")
        }

        // Adapt indicator windows to the candles already visible on screen.
        // Three valid candles are enough for an initial directional assessment.
        val fastPeriod = when {
            candles.size >= 25 -> 9
            candles.size >= 12 -> 5
            else -> 2
        }
        val slowPeriod = when {
            candles.size >= 25 -> 21
            candles.size >= 12 -> 9
            else -> 3
        }
        val rsiPeriod = (candles.size - 1).coerceAtMost(if (candles.size >= 25) 14 else 7).coerceAtLeast(2)
        val atrPeriod = (candles.size - 1).coerceAtMost(if (candles.size >= 25) 14 else 7).coerceAtLeast(2)

        val closes = candles.map { it.close }
        val fast = ema(closes, fastPeriod)
        val slow = ema(closes, slowPeriod)
        val rsiValue = rsi(closes, rsiPeriod)
        val atrValue = atr(candles, atrPeriod)
        val last = candles.last()
        val previous = candles[candles.lastIndex - 1]
        val beforePrevious = candles[candles.lastIndex - 2]

        if (atrValue <= 0.0 || !atrValue.isFinite() || !rsiValue.isFinite()) {
            return noSignal("Недостаточная волатильность или некорректные данные.")
        }
        val atrRatio = atrValue / last.close
        if (atrRatio < 0.000005 || atrRatio > 0.02) {
            return noSignal("Волатильность вне рабочего диапазона.")
        }

        val trendUp = fast > slow
        val trendDown = fast < slow
        val momentumUp = last.close > previous.close && previous.close > beforePrevious.close
        val momentumDown = last.close < previous.close && previous.close < beforePrevious.close
        val trendStrength = abs(fast - slow) / atrValue
        val bullishBody = last.close > last.open && (last.close - last.open) / atrValue >= 0.16
        val bearishBody = last.close < last.open && (last.open - last.close) / atrValue >= 0.16
        val candleRange = (last.high - last.low).coerceAtLeast(1e-12)
        val lowerWick = min(last.open, last.close) - last.low
        val upperWick = last.high - max(last.open, last.close)
        val bullishRejection = last.close > last.open && lowerWick / candleRange >= 0.38
        val bearishRejection = last.close < last.open && upperWick / candleRange >= 0.38

        var callScore = 0
        var putScore = 0
        val callReasons = mutableListOf<String>()
        val putReasons = mutableListOf<String>()

        if (trendUp) { callScore += 20; callReasons += "EMA $fastPeriod выше EMA $slowPeriod" }
        if (trendDown) { putScore += 20; putReasons += "EMA $fastPeriod ниже EMA $slowPeriod" }
        if (last.close > fast) { callScore += 10; callReasons += "Цена выше быстрой EMA" }
        if (last.close < fast) { putScore += 10; putReasons += "Цена ниже быстрой EMA" }
        if (rsiValue in 53.0..68.0) { callScore += 16; callReasons += "RSI поддерживает рост (${rsiValue.toInt()})" }
        else if (rsiValue in 32.0..47.0) { putScore += 16; putReasons += "RSI поддерживает снижение (${rsiValue.toInt()})" }
        if (momentumUp) { callScore += 14; callReasons += "Три свечи показывают восходящий импульс" }
        if (momentumDown) { putScore += 14; putReasons += "Три свечи показывают нисходящий импульс" }
        if (bullishBody) { callScore += 10; callReasons += "Сильное бычье тело последней свечи" }
        if (bearishBody) { putScore += 10; putReasons += "Сильное медвежье тело последней свечи" }
        if (bullishRejection) { callScore += 8; callReasons += "Отбой от нижней тени" }
        if (bearishRejection) { putScore += 8; putReasons += "Отбой от верхней тени" }
        if (trendStrength >= 0.18 && trendUp) { callScore += 10; callReasons += "Тренд подтверждён волатильностью" }
        if (trendStrength >= 0.18 && trendDown) { putScore += 10; putReasons += "Тренд подтверждён волатильностью" }

        if (rsiValue >= 75.0) callScore = (callScore - 12).coerceAtLeast(0)
        if (rsiValue <= 25.0) putScore = (putScore - 12).coerceAtLeast(0)

        val best = max(callScore, putScore)
        if (best < minimumConfidence || abs(callScore - putScore) < 18) {
            return SignalDecision(null, best.coerceAtMost(99), 1,
                listOf("Фильтр SELECTIVE: факторы не дают достаточно сильного перевеса."),
                false, "Сигнала нет. Ожидание более ясной ситуации.")
        }

        val direction = if (callScore > putScore) SignalDirection.CALL else SignalDirection.PUT
        val reasons = if (direction == SignalDirection.CALL) callReasons else putReasons
        val expiration = when {
            best >= 86 && trendStrength >= 0.75 -> 3
            best >= 78 && trendStrength >= 0.45 -> 2
            else -> 1
        }
        return SignalDecision(direction, best.coerceAtMost(92), expiration, reasons, true,
            "Сигнал рассчитан по видимым свечам M1. Процент показывает согласованность факторов, не вероятность выигрыша.")
    }

    private fun noSignal(message: String) = SignalDecision(null, 0, 1, emptyList(), false, message)

    private fun ema(values: List<Double>, period: Int): Double {
        val alpha = 2.0 / (period + 1.0)
        return values.drop(1).fold(values.first()) { previous, value ->
            alpha * value + (1.0 - alpha) * previous
        }
    }

    private fun rsi(values: List<Double>, period: Int): Double {
        var gains = 0.0
        var losses = 0.0
        for (i in values.size - period until values.size) {
            val change = values[i] - values[i - 1]
            if (change > 0.0) gains += change else losses -= change
        }
        val averageGain = gains / period
        val averageLoss = losses / period
        if (averageLoss == 0.0) return if (averageGain == 0.0) 50.0 else 100.0
        val rs = averageGain / averageLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun atr(candles: List<Candle>, period: Int): Double {
        val ranges = (1 until candles.size).map { i ->
            val candle = candles[i]
            val previousClose = candles[i - 1].close
            max(candle.high - candle.low, max(abs(candle.high - previousClose), abs(candle.low - previousClose)))
        }
        return ranges.takeLast(period).average()
    }
}
