// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - deterministic имена export файлов
package com.me4hik.praktika.export

import java.time.LocalDate

object ArchiveExportFilenamePolicy {
    fun suggestedFileName(selection: ExportSelection, format: ExportFormat): String {
        val baseName = when (selection) {
            ExportSelection.All -> "praktika-all"
            is ExportSelection.Day -> "praktika-day-${isoDate(selection.epochDay)}"
            is ExportSelection.Range -> {
                "praktika-period-${isoDate(selection.startEpochDay)}_" +
                    isoDate(selection.endEpochDayInclusive)
            }
            is ExportSelection.Question -> {
                "praktika-question-${selection.questionId.toString().padStart(2, '0')}"
            }
        }
        val extension = when (format) {
            ExportFormat.MARKDOWN -> "md"
            ExportFormat.CSV -> "csv"
            ExportFormat.PDF -> "pdf"
        }
        return "$baseName.$extension"
    }

    private fun isoDate(epochDay: Long): String {
        return LocalDate.ofEpochDay(epochDay).toString()
    }
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
