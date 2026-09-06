// PROMPT 176 — selection subtitle for PDF header
package com.me4hik.praktika.export.pdf

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.ui.archive.ArchiveDisplayFormatter
import java.time.LocalDate
import java.time.ZoneId

internal object PdfArchiveSelectionLabel {
    fun forSelection(
        selection: ExportSelection,
        zoneId: ZoneId,
        displayFormatter: ArchiveDisplayFormatter,
        entries: List<ArchiveEntry>,
    ): String {
        return when (selection) {
            ExportSelection.All -> "Все ответы"
            is ExportSelection.Day -> {
                val millis = LocalDate.ofEpochDay(selection.epochDay)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                displayFormatter.formatDate(millis, zoneId)
            }
            is ExportSelection.Range -> {
                val startMillis = LocalDate.ofEpochDay(selection.startEpochDay)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                val endMillis = LocalDate.ofEpochDay(selection.endEpochDayInclusive)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                "${displayFormatter.formatDate(startMillis, zoneId)} — " +
                    displayFormatter.formatDate(endMillis, zoneId)
            }
            is ExportSelection.Question -> {
                val questionText = entries.firstOrNull()?.questionText?.trim().orEmpty()
                if (questionText.isEmpty()) {
                    "Вопрос ${selection.questionId}"
                } else {
                    "Вопрос ${selection.questionId}: $questionText"
                }
            }
        }
    }
}
