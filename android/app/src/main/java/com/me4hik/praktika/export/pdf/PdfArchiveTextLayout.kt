// PROMPT 176 — pure PDF text wrap / pagination helpers (host-testable)
package com.me4hik.praktika.export.pdf

/**
 * Layout helpers for PDF export that do not depend on [android.graphics.pdf.PdfDocument].
 * Measurement is injected so host unit tests can verify wrap/pagination without graphics.
 */
internal object PdfArchiveTextLayout {
    fun wrapLines(
        text: String,
        maxWidth: Float,
        measureWidth: (String) -> Float,
    ): List<String> {
        require(maxWidth > 0f) { "maxWidth must be positive" }
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isEmpty()) {
            return listOf("")
        }
        val lines = mutableListOf<String>()
        normalized.split('\n').forEach { paragraph ->
            lines.addAll(wrapParagraph(paragraph, maxWidth, measureWidth))
        }
        return lines
    }

    /**
     * Packs sequential line heights into pages of [contentHeight].
     * A single oversized line still occupies its own page slot (caller should avoid that).
     */
    fun pageCount(
        lineHeights: List<Float>,
        contentHeight: Float,
    ): Int {
        require(contentHeight > 0f) { "contentHeight must be positive" }
        if (lineHeights.isEmpty()) {
            return 1
        }
        var pages = 1
        var used = 0f
        for (rawHeight in lineHeights) {
            val height = rawHeight.coerceAtLeast(0f)
            if (used > 0f && used + height > contentHeight) {
                pages++
                used = height.coerceAtMost(contentHeight)
            } else {
                used += height
                if (used > contentHeight) {
                    // First line on a fresh page taller than page: still one page.
                    used = contentHeight
                }
            }
        }
        return pages
    }

    private fun wrapParagraph(
        paragraph: String,
        maxWidth: Float,
        measureWidth: (String) -> Float,
    ): List<String> {
        if (paragraph.isEmpty()) {
            return listOf("")
        }
        if (measureWidth(paragraph) <= maxWidth) {
            return listOf(paragraph)
        }
        val words = paragraph.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            return listOf("")
        }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                appendFitting(word, maxWidth, measureWidth, lines, current)
                continue
            }
            val candidate = "$current $word"
            if (measureWidth(candidate) <= maxWidth) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder()
                appendFitting(word, maxWidth, measureWidth, lines, current)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    private fun appendFitting(
        word: String,
        maxWidth: Float,
        measureWidth: (String) -> Float,
        lines: MutableList<String>,
        current: StringBuilder,
    ) {
        if (measureWidth(word) <= maxWidth) {
            current.append(word)
            return
        }
        var remaining = word
        while (remaining.isNotEmpty()) {
            val fit = longestPrefixFitting(remaining, maxWidth, measureWidth)
            if (fit.isEmpty()) {
                // Pathological measure (e.g. always > maxWidth): force one code point.
                val one = remaining.substring(0, remaining.offsetByCodePoints(0, 1))
                lines.add(one)
                remaining = remaining.substring(one.length)
            } else if (fit.length == remaining.length) {
                current.append(fit)
                remaining = ""
            } else {
                lines.add(fit)
                remaining = remaining.substring(fit.length)
            }
        }
    }

    private fun longestPrefixFitting(
        text: String,
        maxWidth: Float,
        measureWidth: (String) -> Float,
    ): String {
        if (text.isEmpty() || measureWidth(text.take(1)) > maxWidth) {
            return ""
        }
        var low = 1
        var high = text.length
        var best = 1
        while (low <= high) {
            val mid = (low + high) / 2
            val prefix = text.substring(0, mid)
            if (measureWidth(prefix) <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return text.substring(0, best)
    }

    private val WHITESPACE = Regex("\\s+")
}
