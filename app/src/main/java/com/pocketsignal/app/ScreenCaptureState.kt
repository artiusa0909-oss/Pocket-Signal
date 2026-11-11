package com.pocketsignal.app

/**
 * In-process sanitized state only. Raw OCR text, account details and screenshots
 * are discarded after parsing and are never uploaded.
 */
object ScreenCaptureState {
    @Volatile var status: String = "Экран Pocket Option не подключён"
    @Volatile var recognizedTextLength: Int = 0
    @Volatile var detectedAsset: String = ""
    @Volatile var detectedPrice: String = ""
    @Volatile var lastFrameAtMillis: Long = 0L
}
