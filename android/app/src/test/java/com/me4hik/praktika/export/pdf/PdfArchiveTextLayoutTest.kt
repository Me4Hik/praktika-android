// PROMPT 176 — host tests for PDF wrap / pagination / selection label
package com.me4hik.praktika.export.pdf

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.ui.archive.ArchiveDisplayFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfArchiveTextLayoutTest {
    @Test
    fun wrapLines_keepsShortLine() {
        val lines = PdfArchiveTextLayout.wrapLines("короткий", maxWidth = 100f) { sample ->
            sample.length * 10f
        }
        assertEquals(listOf("короткий"), lines)
    }

    @Test
    fun wrapLines_breaksOnWordsAndPreservesNewlines() {
        val measure = { sample: String -> sample.length * 10f }
        val lines = PdfArchiveTextLayout.wrapLines(
            "один два три\nчетыре",
            maxWidth = 60f,
            measureWidth = measure,
        )
        assertEquals(listOf("один", "два", "три", "четыре"), lines)
    }

    @Test
    fun wrapLines_breaksOversizedTokenByCharacters() {
        val measure = { sample: String -> sample.length * 10f }
        val lines = PdfArchiveTextLayout.wrapLines(
            "абвгдежзий",
            maxWidth = 30f,
            measureWidth = measure,
        )
        assertEquals(listOf("абв", "где", "жзи", "й"), lines)
        lines.forEach { line ->
            assertTrue(measure(line) <= 30f)
        }
    }

    @Test
    fun pageCount_splitsWhenContentExceedsPage() {
        val pages = PdfArchiveTextLayout.pageCount(
            lineHeights = listOf(10f, 10f, 10f, 10f, 10f),
            contentHeight = 25f,
        )
        assertEquals(3, pages)
    }

    @Test
    fun pageCount_emptyContentIsSinglePage() {
        assertEquals(1, PdfArchiveTextLayout.pageCount(emptyList(), contentHeight = 100f))
    }
}

class PdfArchiveSelectionLabelTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val formatter = ArchiveDisplayFormatter()

    @Test
    fun allSelectionLabel() {
        assertEquals(
            "Все ответы",
            PdfArchiveSelectionLabel.forSelection(
                ExportSelection.All,
                zone,
                formatter,
                emptyList(),
            ),
        )
    }

    @Test
    fun daySelectionUsesFormattedDate() {
        val day = LocalDate.of(2026, 8, 7).toEpochDay()
        val expectedMillis = LocalDate.of(2026, 8, 7).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(
            formatter.formatDate(expectedMillis, zone),
            PdfArchiveSelectionLabel.forSelection(
                ExportSelection.Day(day),
                zone,
                formatter,
                emptyList(),
            ),
        )
    }

    @Test
    fun rangeSelectionUsesPeriodDash() {
        val start = LocalDate.of(2026, 8, 1).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        val label = PdfArchiveSelectionLabel.forSelection(
            ExportSelection.Range(start, end),
            zone,
            formatter,
            emptyList(),
        )
        assertTrue(label.contains(" — "))
        assertTrue(label.startsWith(formatter.formatDate(
            LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            zone,
        )))
    }

    @Test
    fun questionSelectionIncludesIdAndText() {
        val entry = ArchiveEntry(
            answerId = 1,
            occurrenceId = 1,
            questionId = 3,
            questionText = "Как дела?",
            answerText = "Хорошо",
            answeredAtEpochMillis = millis(2026, 8, 7, 10, 0),
            plannedAtEpochMillis = millis(2026, 8, 7, 9, 0),
            cycleNumber = 1,
            cyclePosition = 1,
        )
        assertEquals(
            "Вопрос 3: Как дела?",
            PdfArchiveSelectionLabel.forSelection(
                ExportSelection.Question(3),
                zone,
                formatter,
                listOf(entry),
            ),
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
    }
}
