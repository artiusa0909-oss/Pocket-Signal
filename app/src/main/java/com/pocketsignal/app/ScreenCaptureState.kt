package com.pocketsignal.app

/** Sanitized in-process state only. Raw OCR text and screenshots are never retained. */
object ScreenCaptureState {
    @Volatile var status = "Экран Pocket Option не подключён"
    @Volatile var recognizedTextLength = 0
    @Volatile var detectedAsset = ""
    @Volatile var detectedPrice = ""
    @Volatile var lastFrameAtMillis = 0L
    @Volatile var lastQuoteAtMillis = 0L
    @Volatile var candleCount = 0
    @Volatile var visibleCandleCount = 0
    @Volatile var chartMovement = "ОЖИДАНИЕ ГРАФИКА"
    @Volatile var chartCalibration = "Шкала цены не откалибрована"
    @Volatile var signalText = "НЕТ СИГНАЛА"
    @Volatile var signalConfidence = 0
    @Volatile var signalExpiryMinutes = 1
    @Volatile var signalReasons = "Ожидание проверенных котировок"
}
