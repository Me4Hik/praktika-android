// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - pure Markdown formatter архива
package com.me4hik.praktika.export.markdown

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ArchiveExportFilenamePolicy
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.ui.archive.ArchiveDisplayFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MarkdownArchiveFormatter(
    private val displayFormatter: ArchiveDisplayFormatter = ArchiveDisplayFormatter(),
) {
    fun format(
        selection: ExportSelection,
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): ExportDocument {
        val content = when (selection) {
            is ExportSelection.Question -> formatQuestionHistory(entries, zoneId)
            is ExportSelection.Day -> formatDay(selection.epochDay, entries, zoneId)
            is ExportSelection.Range -> formatRange(selection, entries, zoneId)
            ExportSelection.All -> formatAll(entries, zoneId)
        }
        return ExportDocument(
            suggestedFileName = ArchiveExportFilenamePolicy.suggestedFileName(
                selection,
                ExportFormat.MARKDOWN,
            ),
            mimeType = MIME_TYPE,
            bytes = content.toByteArray(Charsets.UTF_8),
        )
    }

    companion object {
        const val MIME_TYPE = "text/markdown"
    }

    private fun formatAll(entries: List<ArchiveEntry>, zoneId: ZoneId): String {
        return buildString {
            appendLine("# Архив ответов")
            appendLine()
            appendGroupedEntries(entries, zoneId)
        }.trimEnd()
    }
    private fun formatDay(epochDay: Long, entries: List<ArchiveEntry>, zoneId: ZoneId): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return buildString {
            appendLine("# Ответы за ${displayFormatter.formatDate(date.atStartOfDay(zoneId).toInstant().toEpochMilli(), zoneId)}")
            appendLine()
            appendDayEntries(sortNewestFirst(entries), zoneId, includeDateHeading = false)
        }.trimEnd()
    }

    private fun formatRange(selection: ExportSelection.Range, entries: List<ArchiveEntry>, zoneId: ZoneId): String {
        val startDate = LocalDate.ofEpochDay(selection.startEpochDay)
        val endDate = LocalDate.ofEpochDay(selection.endEpochDayInclusive)
        val startMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return buildString {
            appendLine("# Ответы за период")
            appendLine()
            appendLine("${displayFormatter.formatDate(startMillis, zoneId)} — ${displayFormatter.formatDate(endMillis, zoneId)}")
            appendLine()
            appendGroupedEntries(entries, zoneId)
        }.trimEnd()
    }

    private fun formatQuestionHistory(entries: List<ArchiveEntry>, zoneId: ZoneId): String {
        val ordered = sortOldestFirst(entries)
        val snapshots = ordered.map { it.questionText }.distinct()
        return buildString {
            appendLine("# История ответов")
            appendLine()
            if (snapshots.size == 1) {
                appendLine("## Вопрос")
                appendLine()
                appendLine(MarkdownUserTextEscaper.escapeBlock(snapshots.first()))
                appendLine()
            }
            ordered.forEach { entry ->
                appendLine(
                    "### Цикл ${entry.cycleNumber} — " +
                        displayFormatter.formatDateTimeLine(entry.answeredAtEpochMillis, zoneId),
                )
                appendLine()
                if (snapshots.size > 1) {
                    appendLine("**Вопрос**")
                    appendLine()
                    appendLine(MarkdownUserTextEscaper.escapeBlock(entry.questionText))
                    appendLine()
                }
                appendLine("**Ответ**")
                appendLine()
                appendLine(MarkdownUserTextEscaper.escapeBlock(entry.answerText))
                appendLine()
            }
        }.trimEnd()
    }

    private fun StringBuilder.appendGroupedEntries(entries: List<ArchiveEntry>, zoneId: ZoneId) {
        groupByDateDescending(entries, zoneId).forEach { (date, dayEntries) ->
            val dateMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            appendLine("## ${displayFormatter.formatDate(dateMillis, zoneId)}")
            appendLine()
            appendDayEntries(dayEntries, zoneId, includeDateHeading = false)
        }
    }

    private fun StringBuilder.appendDayEntries(
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
        includeDateHeading: Boolean,
    ) {
        entries.forEachIndexed { index, entry ->
            appendLine("### ${MarkdownUserTextEscaper.escapeBlock(entry.questionText).replace("\n", " ")}")
            appendLine()
            appendLine("**Ответ**")
            appendLine()
            appendLine(MarkdownUserTextEscaper.escapeBlock(entry.answerText))
            appendLine()
            appendLine("**Время:** ${displayFormatter.formatTime(entry.answeredAtEpochMillis, zoneId)}  ")
            appendLine("**Цикл:** ${entry.cycleNumber}")
            if (index < entries.lastIndex) {
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun groupByDateDescending(
        entries: List<ArchiveEntry>,
        zoneId: ZoneId,
    ): List<Pair<LocalDate, List<ArchiveEntry>>> {
        return entries
            .groupBy { entry ->
                Instant.ofEpochMilli(entry.answeredAtEpochMillis).atZone(zoneId).toLocalDate()
            }
            .toList()
            .sortedByDescending { it.first }
            .map { (date, groupedEntries) ->
                date to sortNewestFirst(groupedEntries)
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
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
