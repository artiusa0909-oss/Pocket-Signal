package com.pocketsignal.app

/** One completed OHLC candle from a verified market-data source. */
data class Candle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0
)

enum class SignalDirection { CALL, PUT }

data class SignalDecision(
    val direction: SignalDirection?,
    val confidence: Int,
    val expirationMinutes: Int,
    val reasons: List<String>,
    val ready: Boolean,
    val message: String
)
