package com.pocketsignal.app

/**
 * Normalized market tick. Values must originate from a verified quote feed;
 * the application must never synthesize missing market prices.
 */
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
 * Stateful M1 candle builder. It accepts ticks in chronological order only.
 * A caller must detect feed gaps and stale data separately.
 */
class M1CandleAggregator {
    private val current = LinkedHashMap<String, M1Candle>()

    fun accept(tick: MarketTick): M1Candle? {
        require(tick.symbol.isNotBlank()) { "Symbol must not be blank." }
        require(tick.timestampMillis > 0L) { "Tick timestamp must be positive." }
        require(tick.price.isFinite() && tick.price > 0.0) { "Tick price is invalid." }

        val minute = tick.timestampMillis / 60_000L * 60_000L
        val previous = current[tick.symbol]

        if (previous == null) {
            current[tick.symbol] = M1Candle(
                symbol = tick.symbol,
                minuteStartMillis = minute,
                open = tick.price,
                high = tick.price,
                low = tick.price,
                close = tick.price,
                tickCount = 1
            )
            return null
        }

        // Reject out-of-order ticks rather than silently corrupting OHLC.
        if (minute < previous.minuteStartMillis) return null

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
        current[tick.symbol] = M1Candle(
            symbol = tick.symbol,
            minuteStartMillis = minute,
            open = tick.price,
            high = tick.price,
            low = tick.price,
            close = tick.price,
            tickCount = 1
        )
        return completed
    }

    fun snapshot(symbol: String): M1Candle? = current[symbol]

    fun clear() = current.clear()
}
