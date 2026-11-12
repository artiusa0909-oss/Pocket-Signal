package com.pocketsignal.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ChartPriceAxisTick(val price: Double, val y: Float)
data class ChartVisionResult(
    val visibleCandleCount: Int,
    val movement: String,
    val candles: List<Candle>,
    val calibrated: Boolean
)

/**
 * Extracts colored candle columns directly from the captured chart bitmap.
 * It deliberately rejects broad colored UI elements and does not invent candles
 * when the price axis cannot be calibrated.
 */
object ChartCandleVisionDetector {
    private data class Column(val direction: Int, val minY: Int, val maxY: Int, val pixels: Int)
    private data class Group(val direction: Int, val left: Int, val right: Int)

    fun detect(bitmap: Bitmap, axisTicks: List<ChartPriceAxisTick>, nowMillis: Long): ChartVisionResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 100 || height < 100) return ChartVisionResult(0, "НЕ ОПРЕДЕЛЕНО", emptyList(), false)

        val xStart = (width * 0.035f).toInt().coerceAtLeast(0)
        val xEnd = (width * 0.875f).toInt().coerceAtMost(width - 1)
        val yStart = (height * 0.14f).toInt().coerceAtLeast(0)
        val yEnd = (height * 0.86f).toInt().coerceAtMost(height - 1)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        fun direction(color: Int): Int {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val delta = hi - lo
            if (hi < 0.30f || delta < 0.12f || hi <= 0f) return 0
            val saturation = delta / hi
            if (saturation < 0.34f) return 0
            val hue = when (hi) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            return when {
                hue in 72f..178f -> 1
                hue <= 18f || hue >= 342f -> -1
                else -> 0
            }
        }

        val columns = arrayOfNulls<Column>(width)
        for (x in xStart..xEnd) {
            var greenCount = 0
            var redCount = 0
            var greenMin = yEnd
            var greenMax = yStart
            var redMin = yEnd
            var redMax = yStart
            for (y in yStart..yEnd) {
                val d = direction(pixels[y * width + x])
                if (d > 0) {
                    greenCount++
                    greenMin = min(greenMin, y)
                    greenMax = max(greenMax, y)
                } else if (d < 0) {
                    redCount++
                    redMin = min(redMin, y)
                    redMax = max(redMax, y)
                }
            }
            columns[x] = when {
                greenCount > redCount && greenCount >= 2 -> Column(1, greenMin, greenMax, greenCount)
                redCount > greenCount && redCount >= 2 -> Column(-1, redMin, redMax, redCount)
                else -> null
            }
        }

        val groups = mutableListOf<Group>()
        var groupStart = -1
        var groupEnd = -1
        var groupDirection = 0
        for (x in xStart..(xEnd + 1)) {
            val col = if (x <= xEnd) columns[x] else null
            if (col != null && (groupStart < 0 || (col.direction == groupDirection && x - groupEnd <= 3))) {
                if (groupStart < 0) {
                    groupStart = x
                    groupDirection = col.direction
                }
                groupEnd = x
            } else {
                if (groupStart >= 0) {
                    val groupWidth = groupEnd - groupStart + 1
                    if (groupWidth <= 20) groups += Group(groupDirection, groupStart, groupEnd)
                }
                groupStart = if (col != null) x else -1
                groupEnd = if (col != null) x else -1
                groupDirection = col?.direction ?: 0
            }
        }

        val detected = groups.mapNotNull { group ->
            val groupWidth = group.right - group.left + 1
            val rowCounts = IntArray(yEnd - yStart + 1)
            var minY = yEnd
            var maxY = yStart
            var total = 0
            for (x in group.left..group.right) {
                for (y in yStart..yEnd) {
                    if (direction(pixels[y * width + x]) == group.direction) {
                        rowCounts[y - yStart]++
                        minY = min(minY, y)
                        maxY = max(maxY, y)
                        total++
                    }
                }
            }
            if (total < 5 || maxY - minY < 4) return@mapNotNull null

            val threshold = max(2, (groupWidth * 0.38f).toInt())
            var bestStart = -1
            var bestEnd = -1
            var runStart = -1
            for (i in rowCounts.indices) {
                if (rowCounts[i] >= threshold) {
                    if (runStart < 0) runStart = i
                } else if (runStart >= 0) {
                    if (bestStart < 0 || i - runStart > bestEnd - bestStart) {
                        bestStart = runStart
                        bestEnd = i - 1
                    }
                    runStart = -1
                }
            }
            if (runStart >= 0 && (bestStart < 0 || rowCounts.size - runStart > bestEnd - bestStart)) {
                bestStart = runStart
                bestEnd = rowCounts.lastIndex
            }
            val bodyTop = if (bestStart >= 0 && bestEnd >= bestStart) yStart + bestStart else minY
            val bodyBottom = if (bestStart >= 0 && bestEnd >= bestStart) yStart + bestEnd else maxY
            if (bodyBottom - bodyTop < 1) return@mapNotNull null
            Detected(group.left + group.right, minY, maxY, bodyTop, bodyBottom, group.direction)
        }.sortedBy { it.centerX }

        val last = detected.lastOrNull()
        val movement = when {
            last == null -> "СВЕЧИ НЕ НАЙДЕНЫ"
            last.direction > 0 -> "ДВИЖЕНИЕ ВВЕРХ"
            else -> "ДВИЖЕНИЕ ВНИЗ"
        }

        val mapping = fitPriceScale(axisTicks)
        if (mapping == null) return ChartVisionResult(detected.size, movement, emptyList(), false)
        val (intercept, slope) = mapping
        val bars = detected.mapIndexedNotNull { index, c ->
            fun price(y: Int) = intercept + slope * y
            val high = price(c.highY)
            val low = price(c.lowY)
            val open = price(if (c.direction > 0) c.bodyBottomY else c.bodyTopY)
            val close = price(if (c.direction > 0) c.bodyTopY else c.bodyBottomY)
            if (listOf(high, low, open, close).any { !it.isFinite() || it <= 0.0 } || high < low) null
            else Candle(nowMillis / 60_000L * 60_000L - (detected.size - 1 - index) * 60_000L,
                open, max(high, max(open, close)), min(low, min(open, close)), close)
        }
        return ChartVisionResult(detected.size, movement, bars, bars.size >= 3)
    }

    private data class Detected(
        val centerX: Int, val highY: Int, val lowY: Int,
        val bodyTopY: Int, val bodyBottomY: Int, val direction: Int
    )

    private fun fitPriceScale(ticks: List<ChartPriceAxisTick>): Pair<Double, Double>? {
        val points = ticks.filter { it.price.isFinite() && it.price > 0.0 && it.y.isFinite() }
            .distinctBy { (it.price to it.y.toInt()) }
        if (points.size < 2) return null
        val meanY = points.map { it.y.toDouble() }.average()
        val meanP = points.map { it.price }.average()
        val denominator = points.sumOf { (it.y - meanY) * (it.y - meanY) }
        if (denominator <= 1.0) return null
        val slope = points.sumOf { (it.y - meanY) * (it.price - meanP) } / denominator
        if (!slope.isFinite() || slope >= 0.0) return null
        val intercept = meanP - slope * meanY
        val range = (points.maxOf { it.price } - points.minOf { it.price }).coerceAtLeast(1e-12)
        val rms = sqrt(points.map { (it.price - (intercept + slope * it.y)).let { e -> e * e } }.average())
        if (rms / range > 0.18) return null
        return intercept to slope
    }
}
