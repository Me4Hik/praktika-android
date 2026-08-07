// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - pure CSV formatter архива
package com.me4hik.praktika.export.csv

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ArchiveExportFilenamePolicy
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CsvArchiveFormatter {
    fun format(
        selection: ExportSelection,
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): ExportDocument {
        val orderedEntries = when (selection) {
            is ExportSelection.Question -> sortOldestFirst(entries)
            else -> sortNewestFirst(entries)
        }
        val body = buildString {
            append(HEADER)
            orderedEntries.forEach { entry ->
                append(ROW_SEPARATOR)
                append(formatRow(entry, zoneId))
            }
        }
        val bytes = UTF8_BOM + body.toByteArray(Charsets.UTF_8)
        return ExportDocument(
            suggestedFileName = ArchiveExportFilenamePolicy.suggestedFileName(
                selection,
                ExportFormat.CSV,
            ),
            mimeType = MIME_TYPE,
            bytes = bytes,
        )
    }

    private fun formatRow(entry: ArchiveEntry, zoneId: ZoneId): String {
        val zonedDateTime = Instant.ofEpochMilli(entry.answeredAtEpochMillis).atZone(zoneId)
        return listOf(
            entry.answerId.toString(),
            entry.questionId.toString(),
            escapeField(entry.questionText),
            "",
            escapeField(entry.answerText),
            zonedDateTime.format(DATE_FORMATTER),
            zonedDateTime.format(TIME_FORMATTER),
            entry.cycleNumber.toString(),
        ).joinToString(FIELD_SEPARATOR)
    }

    internal companion object {
        const val HEADER = "answer_id,question_id,question,topic,answer,date,time,cycle_number"
        const val MIME_TYPE = "text/csv"
        const val FIELD_SEPARATOR = ","
        const val ROW_SEPARATOR = "\r\n"
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        fun escapeField(value: String): String {
            val needsQuoting = value.any { character ->
                character == ',' || character == '"' || character == '\r' || character == '\n'
            }
            if (!needsQuoting) {
                return value
            }
            return buildString(value.length + 2) {
                append('"')
                value.forEach { character ->
                    if (character == '"') {
                        append("\"\"")
                    } else {
                        append(character)
                    }
                }
                append('"')
            }
        }

        private fun sortNewestFirst(entries: List<ArchiveEntry>): List<ArchiveEntry> {
            return entries.sortedWith(
                compareByDescending<ArchiveEntry> { it.answeredAtEpochMillis }
                    .thenByDescending { it.answerId },
            )
        }

        private fun sortOldestFirst(entries: List<ArchiveEntry>): List<ArchiveEntry> {
            return entries.sortedWith(
                compareBy<ArchiveEntry> { it.answeredAtEpochMillis }
                    .thenBy { it.answerId },
            )
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
