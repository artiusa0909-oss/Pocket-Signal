package com.pocketsignal.app

/**
 * Conservative M1/M5/M15 confluence gate.
 *
 * Every timeframe must independently produce a valid candidate in the same
 * direction. Missing data, WAIT, or disagreement suppresses the combined signal.
 * This class consumes supplied candles; it does not connect to broker internals.
 */
class MultiTimeframeSignalEngine(
    private val signalEngine: SignalEngine = SignalEngine()
) {
    private val requiredTimeframes = listOf("M1", "M5", "M15")

    fun analyze(series: Map<String, List<Candle>>): SignalDecision {
        val missing = requiredTimeframes.filter { series[it].isNullOrEmpty() }
        if (missing.isNotEmpty()) {
            return noSignal("Нет данных таймфреймов: ${missing.joinToString()}.")
        }

        val analyses = requiredTimeframes.map { timeframe ->
            timeframe to signalEngine.analyze(series.getValue(timeframe))
        }
        val blocked = analyses.filter { !it.second.ready || it.second.direction == null }
        if (blocked.isNotEmpty()) {
            return noSignal(
                "Ожидание подтверждения M1/M5/M15: " +
                    blocked.joinToString { "${it.first} — ${it.second.message}" }
            )
        }

        val directions = analyses.mapNotNull { it.second.direction }.distinct()
        if (directions.size != 1) {
            return noSignal("Таймфреймы M1/M5/M15 расходятся по направлению. Сигнал отменён.")
        }

        val decisions = analyses.map { it.second }
        return SignalDecision(
            direction = directions.single(),
            confidence = decisions.minOf { it.confidence },
            expirationMinutes = decisions.minOf { it.expirationMinutes },
            reasons = analyses.flatMap { (timeframe, decision) ->
                decision.reasons.take(2).map { "$timeframe: $it" }
            },
            ready = true,
            message = "M1, M5 и M15 подтвердили одно направление. Оценка стратегии, не вероятность выигрыша."
        )
    }

    private fun noSignal(message: String) =
        SignalDecision(null, 0, 1, emptyList(), false, message)
}
