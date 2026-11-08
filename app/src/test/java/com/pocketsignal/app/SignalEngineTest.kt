package com.pocketsignal.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {
    private val engine = SignalEngine()

    @Test
    fun insufficientHistoryNeverProducesSignal() {
        val result = engine.analyze(validCandles(34))
        assertFalse(result.ready)
        assertNull(result.direction)
    }

    @Test
    fun outOfOrderCandlesAreRejectedInsteadOfReordered() {
        val candles = validCandles()
        val invalidOrder = candles.toMutableList().apply {
            val first = this[10]
            this[10] = this[11]
            this[11] = first
        }

        val result = engine.analyze(invalidOrder)
        assertFalse(result.ready)
        assertTrue(result.message.contains("порядок"))
    }

    @Test
    fun malformedOhlcIsRejectedInsteadOfSilentlyDropped() {
        val candles = validCandles().toMutableList()
        candles[20] = candles[20].copy(high = candles[20].low - 1.0)

        val result = engine.analyze(candles)
        assertFalse(result.ready)
        assertTrue(result.message.contains("некорректная свеча"))
    }

    @Test
    fun duplicateTimestampsAreRejected() {
        val candles = validCandles().toMutableList()
        candles[20] = candles[20].copy(timestampMillis = candles[19].timestampMillis)

        val result = engine.analyze(candles)
        assertFalse(result.ready)
        assertTrue(result.message.contains("повтор"))
    }

    @Test
    fun multitimeframeEngineWaitsWhenAnyTimeframeIsMissing() {
        val result = MultiTimeframeSignalEngine().analyze(
            mapOf("M1" to validCandles(), "M5" to validCandles())
        )

        assertFalse(result.ready)
        assertNull(result.direction)
        assertTrue(result.message.contains("M15"))
    }

    @Test
    fun m1AggregatorEmitsOnlyCompletedCandle() {
        val aggregator = M1CandleAggregator()

        assertNull(aggregator.accept(MarketTick("EURUSD", 60_000L, 1.10)))
        assertNull(aggregator.accept(MarketTick("EURUSD", 90_000L, 1.12)))

        val completed = aggregator.accept(MarketTick("EURUSD", 120_000L, 1.11))
        assertEquals(60_000L, completed?.minuteStartMillis)
        assertEquals(1.10, completed?.open ?: 0.0, 0.000001)
        assertEquals(1.12, completed?.high ?: 0.0, 0.000001)
        assertEquals(1.10, completed?.low ?: 0.0, 0.000001)
        assertEquals(1.12, completed?.close ?: 0.0, 0.000001)
    }

    @Test
    fun aggregatorIgnoresOutOfOrderTicks() {
        val aggregator = M1CandleAggregator()
        aggregator.accept(MarketTick("EURUSD", 60_000L, 1.10))
        aggregator.accept(MarketTick("EURUSD", 90_000L, 1.12))

        assertNull(aggregator.accept(MarketTick("EURUSD", 80_000L, 1.50)))
        assertEquals(1.12, aggregator.snapshot("EURUSD")?.close ?: 0.0, 0.000001)
    }

    private fun validCandles(count: Int = 35): List<Candle> =
        (0 until count).map { index ->
            val close = 1.10 + index * 0.0001
            Candle(
                timestampMillis = 1_800_000_000_000L + index * 60_000L,
                open = close - 0.00002,
                high = close + 0.00003,
                low = close - 0.00004,
                close = close
            )
        }
}
