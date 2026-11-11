package com.pocketsignal.app

/**
 * In-process status only. Captured pixels and OCR results are never uploaded.
 */
object ScreenCaptureState {
    @Volatile var status: String = "Экран Pocket Option не подключён"
    @Volatile var recognizedTextLength: Int = 0
    @Volatile var lastFrameAtMillis: Long = 0L
}
