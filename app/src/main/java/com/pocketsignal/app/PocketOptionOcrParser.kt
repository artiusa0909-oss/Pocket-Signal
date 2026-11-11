package com.pocketsignal.app

/**
 * Conservative OCR extraction. A single visible numeric candidate is exposed
 * for inspection only; it is never converted into a candle or trading signal.
 */
data class OcrMarketSnapshot(
    val asset: String?,
    val priceText: String?
)

object PocketOptionOcrParser {
    private val assetPattern = Regex("(?i)\\b([A-Z]{3})\\s*/\\s*([A-Z]{3})(\\s*OTC)?\\b")
    private val pricePattern = Regex("(?<![\\d.,])\\d{1,6}[.,]\\d{2,8}(?![\\d.,%])")
    private val excludedLabels = listOf(
        "balance", "profit", "payout", "收益", "余额", "прибыл", "баланс",
        "выплат", "инвести", "investment", "amount", "сумм", "time", "время"
    )
    private val priceLabels = listOf("price", "rate", "quote", "цена", "курс")

    fun parse(rawText: String): OcrMarketSnapshot {
        val lines = rawText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val asset = lines.asSequence()
            .mapNotNull { line -> assetPattern.find(line) }
            .map { match ->
                val otc = match.groupValues[3].isNotBlank()
                "${match.groupValues[1].uppercase()}/${match.groupValues[2].uppercase()}" +
                    if (otc) " OTC" else ""
            }
            .firstOrNull()

        val candidates = lines.flatMap { line ->
            val normalized = line.lowercase()
            if (excludedLabels.any { normalized.contains(it) }) {
                emptyList()
            } else {
                pricePattern.findAll(line).map { it.value.replace(',', '.') }.toList()
            }
        }.distinct()

        val preferred = lines.asSequence()
            .filter { line ->
                val normalized = line.lowercase()
                priceLabels.any { normalized.contains(it) } &&
                    excludedLabels.none { normalized.contains(it) }
            }
            .flatMap { line -> pricePattern.findAll(line).map { it.value.replace(',', '.') } }
            .distinct()
            .toList()

        val price = when {
            preferred.size == 1 -> preferred.single()
            preferred.size > 1 -> null
            candidates.size == 1 -> candidates.single()
            else -> null
        }
        return OcrMarketSnapshot(asset, price)
    }
}
