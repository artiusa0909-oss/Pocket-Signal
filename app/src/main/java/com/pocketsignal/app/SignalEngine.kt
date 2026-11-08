package com.pocketsignal.app

import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic technical-analysis baseline for the future combined_ml pipeline.
 *
 * This is not a trained ML model and its score is not a win probability.
 * Only completed, validated candles may be analyzed. A verified market-data
 * adapter and out-of-sample backtest are required before live signals are enabled.
 */
class SignalEngine(
    private val minimumConfidence: Int = 72,
    private val minimumCandles: Int = 35
) {
    fun analyze(input: List<Candle>): SignalDecision {
        if (input.size < minimumCandles) {
            return noSignal("Недостаточно свечей: требуется минимум $minimumCandles, получено ${input.size}.")
        }

        // Do not sort, deduplicate, or silently discard bad candles: doing so can
        // hide a broken feed and produce a plausible-looking but invalid signal.
        for (index in input.indices) {
            val candle = input[index]
            val valid = candle.timestampMillis > 0L &&
                candle.open.isFinite() && candle.high.isFinite() &&
                candle.low.isFinite() && candle.close.isFinite() &&
                candle.open > 0.0 && candle.high > 0.0 &&
                candle.low > 0.0 && candle.close > 0.0 &&
                candle.high >= max(candle.open, candle.close) &&
                candle.low <= minOf(candle.open, candle.close) &&
                candle.high >= candle.low &&
                candle.volume.isFinite() && candle.volume >= 0.0
            if (!valid) {
                return noSignal("Обнаружена некорректная свеча. Анализ остановлен.")
            }
            if (index > 0 && candle.timestampMillis <= input[index - 1].timestampMillis) {
                return noSignal("Нарушен порядок времени или обнаружен повтор свечи.")
            }
        }

        val closes = input.map { it.close }
        val fast = ema(closes, 9)
        val slow = ema(closes, 21)
        val rsiValue = rsiWilder(closes, 14)
        val atrValue = atrWilder(input, 14)
        val last = input.last()
        val previous = input[input.lastIndex - 1]

        if (atrValue <= 0.0 || !atrValue.isFinite() || !rsiValue.isFinite()) {
            return noSignal("Недостаточная волатильность или некорректные данные.")
        }
        val atrRatio = atrValue / last.close
        if (atrRatio < 0.00002 || atrRatio > 0.01) {
            return noSignal("Волатильность вне рабочего диапазона стратегии.")
        }

        val up = fast > slow
        val down = fast < slow
        val momentumUp = last.close > previous.close &&
            previous.close > input[input.lastIndex - 2].close
        val momentumDown = last.close < previous.close &&
            previous.close < input[input.lastIndex - 2].close
        val strongTrend = abs(fast - slow) / atrValue >= 0.15
        val bullishBody = last.close > last.open &&
            (last.close - last.open) / atrValue >= 0.20
        val bearishBody = last.close < last.open &&
            (last.open - last.close) / atrValue >= 0.20

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
            return SignalDecision(
                null,
                best,
                1,
                listOf("Фильтр SELECTIVE: недостаточно сильное или однозначное совпадение факторов."),
                false,
                "Сильного сигнала нет — пропуск сделки."
            )
        }

        val direction = if (callScore > putScore) SignalDirection.CALL else SignalDirection.PUT
        val reasons = if (direction == SignalDirection.CALL) callReasons else putReasons
        val trendStrength = abs(fast - slow) / atrValue
        val expiration = when {
            best >= 86 && trendStrength >= 0.8 -> 3
            best >= 80 && trendStrength >= 0.5 -> 2
            else -> 1
        }
        return SignalDecision(
            direction,
            best.coerceAtMost(95),
            expiration,
            reasons,
            true,
            "Кандидат рассчитан на закрытых свечах; требуется проверка на исторических данных."
        )
    }

    private fun noSignal(message: String) =
        SignalDecision(null, 0, 1, emptyList(), false, message)

    /** EMA with an SMA seed, reducing initialization bias on short histories. */
    private fun ema(values: List<Double>, period: Int): Double {
        val seed = values.take(period).average()
        val alpha = 2.0 / (period + 1.0)
        return values.drop(period).fold(seed) { previous, value ->
            alpha * value + (1.0 - alpha) * previous
        }
    }

    /** Wilder-smoothed RSI(14), rather than a simple average of only the last 14 moves. */
    private fun rsiWilder(values: List<Double>, period: Int): Double {
        var averageGain = 0.0
        var averageLoss = 0.0
        for (index in 1..period) {
            val change = values[index] - values[index - 1]
            averageGain += max(change, 0.0)
            averageLoss += max(-change, 0.0)
        }
        averageGain /= period
        averageLoss /= period

        for (index in period + 1 until values.size) {
            val change = values[index] - values[index - 1]
            averageGain = (averageGain * (period - 1) + max(change, 0.0)) / period
            averageLoss = (averageLoss * (period - 1) + max(-change, 0.0)) / period
        }

        if (averageLoss == 0.0) return if (averageGain == 0.0) 50.0 else 100.0
        val relativeStrength = averageGain / averageLoss
        return 100.0 - 100.0 / (1.0 + relativeStrength)
    }

    /** Wilder-smoothed ATR(14) based on true range, including previous-close gaps. */
    private fun atrWilder(candles: List<Candle>, period: Int): Double {
        val trueRanges = (1 until candles.size).map { index ->
            val candle = candles[index]
            val previousClose = candles[index - 1].close
            max(
                candle.high - candle.low,
                max(abs(candle.high - previousClose), abs(candle.low - previousClose))
            )
        }
        if (trueRanges.size < period) return 0.0
        var value = trueRanges.take(period).average()
        for (index in period until trueRanges.size) {
            value = (value * (period - 1) + trueRanges[index]) / period
        }
        return value
    }
}
