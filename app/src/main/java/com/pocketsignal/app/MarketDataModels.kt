package com.pocketsignal.app

/** Normalized market tick from a verified quote source. */
data class MarketTick(
    val symbol: String,
    val timestampMillis: Long,
    val price: Double
)

/** One-minute OHLC candle built from incoming ticks. */
data class M1Candle(
    val symbol: String,
    val minuteStartMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val tickCount: Int
)

/**
 * Builds M1 candles from chronological ticks.
 * Missing minutes are not fabricated; the caller must track feed gaps.
 */
class M1CandleAggregator {
    private val current = LinkedHashMap<String, M1Candle>()
    private val lastTimestampBySymbol = HashMap<String, Long>()

    fun accept(tick: MarketTick): M1Candle? {
        require(tick.symbol.isNotBlank()) { "Symbol must not be blank." }
        require(tick.timestampMillis > 0L) { "Tick timestamp must be positive." }
        require(tick.price.isFinite() && tick.price > 0.0) { "Tick price is invalid." }

        val lastTimestamp = lastTimestampBySymbol[tick.symbol]
        if (lastTimestamp != null && tick.timestampMillis <= lastTimestamp) return null

        val minute = tick.timestampMillis / 60_000L * 60_000L
        val previous = current[tick.symbol]
        lastTimestampBySymbol[tick.symbol] = tick.timestampMillis

        if (previous == null) {
            current[tick.symbol] = newCandle(tick, minute)
            return null
        }

        if (minute == previous.minuteStartMillis) {
            current[tick.symbol] = previous.copy(
                high = maxOf(previous.high, tick.price),
                low = minOf(previous.low, tick.price),
                close = tick.price,
                tickCount = previous.tickCount + 1
            )
            return null
        }

        val completed = previous
        current[tick.symbol] = newCandle(tick, minute)
        return completed
    }

    fun snapshot(symbol: String): M1Candle? = current[symbol]

    fun clear() {
        current.clear()
        lastTimestampBySymbol.clear()
    }

    private fun newCandle(tick: MarketTick, minute: Long) = M1Candle(
        symbol = tick.symbol,
        minuteStartMillis = minute,
        open = tick.price,
        high = tick.price,
        low = tick.price,
        close = tick.price,
        tickCount = 1
    )
}
