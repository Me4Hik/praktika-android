// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - test CSV parser round-trip
package com.me4hik.praktika.export.csv

internal object CsvTestParser {
    fun parse(text: String): ParsedCsv {
        val rows = mutableListOf<List<String>>()
        var index = 0
        while (index < text.length) {
            val row = mutableListOf<String>()
            while (index < text.length) {
                val (value, nextIndex) = parseCell(text, index)
                row += value
                index = nextIndex
                if (index < text.length && text[index] == ',') {
                    index += 1
                    continue
                }
                break
            }
            rows += row
            while (index < text.length && (text[index] == '\r' || text[index] == '\n')) {
                index += if (text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                    2
                } else {
                    1
                }
            }
        }
        return ParsedCsv(rows)
    }

    private fun parseCell(text: String, startIndex: Int): Pair<String, Int> {
        if (text[startIndex] == '"') {
            val builder = StringBuilder()
            var index = startIndex + 1
            while (index < text.length) {
                when (text[index]) {
                    '"' -> {
                        if (index + 1 < text.length && text[index + 1] == '"') {
                            builder.append('"')
                            index += 2
                        } else {
                            return builder.toString() to index + 1
                        }
                    }
                    else -> {
                        builder.append(text[index])
                        index += 1
                    }
                }
            }
            error("Unterminated quoted CSV field")
        }
        val endIndex = findCellEnd(text, startIndex)
        return text.substring(startIndex, endIndex) to endIndex
    }

    private fun findCellEnd(text: String, startIndex: Int): Int {
        var index = startIndex
        while (index < text.length) {
            when (text[index]) {
                ',', '\r', '\n' -> return index
                else -> index += 1
            }
        }
        return index
    }
}

internal data class ParsedCsv(
    val rows: List<List<String>>,
) {
    val header: List<String>
        get() = rows.first()

    val dataRows: List<List<String>>
        get() = rows.drop(1)
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
