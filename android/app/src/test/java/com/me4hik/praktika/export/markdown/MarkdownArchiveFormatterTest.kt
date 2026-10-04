// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - unit tests MarkdownArchiveFormatter
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - ExportDocument bytes regression
package com.me4hik.praktika.export.markdown

import com.me4hik.praktika.data.preferences.AppLanguage
import com.me4hik.praktika.data.preferences.AppLocaleController
import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.ui.archive.ArchiveDisplayFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MarkdownArchiveFormatterTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val formatter = MarkdownArchiveFormatter(ArchiveDisplayFormatter())

    @Before
    fun setUp() {
        AppLocaleController.apply(AppLanguage.RU)
    }

    @Test
    fun oneAnswerAllExport() {
        val entry = entry(
            answerId = 1,
            at = epochMillis(2026, 8, 7, 18, 42),
            questionText = "Как я себя чувствую?",
            answerText = "Сегодня спокойно.",
            cycleNumber = 2,
        )
        val document = formatter.format(ExportSelection.All, listOf(entry), zone)
        assertEquals("praktika-all.md", document.suggestedFileName)
        assertEquals(MarkdownArchiveFormatter.MIME_TYPE, document.mimeType)
        val content = document.textContent()
        assertTrue(content.startsWith("# Архив ответов"))
        assertTrue(content.contains("### Как я себя чувствую?"))
        assertTrue(content.contains("Сегодня спокойно."))
        assertTrue(content.contains("**Время:** 18:42"))
        assertTrue(content.contains("**Цикл:** 2"))
    }

    @Test
    fun markdownExportHasNoBom() {
        val document = formatter.format(
            ExportSelection.All,
            listOf(entry(1, epochMillis(2026, 8, 7, 12, 0), "Q", "A", 1)),
            zone,
        )
        assertFalse(document.bytes.copyOfRange(0, minOf(3, document.bytes.size))
            .contentEquals(CsvArchiveFormatter.UTF8_BOM))
    }

    @Test
    fun multipleAnswersSameDay() {
        val day = epochMillis(2026, 8, 7, 11, 0)
        val later = epochMillis(2026, 8, 7, 18, 0)
        val entries = listOf(
            entry(1, later, "Q2", "A2", 2),
            entry(2, day, "Q1", "A1", 1),
        )
        val content = formatter.format(ExportSelection.All, entries, zone).textContent()
        assertTrue(content.indexOf("Q2") < content.indexOf("Q1"))
    }

    @Test
    fun multipleDaysNewestDayFirst() {
        val dayOne = entry(1, epochMillis(2026, 8, 5, 10, 0), "Old", "Old answer", 1)
        val dayTwo = entry(2, epochMillis(2026, 8, 7, 10, 0), "New", "New answer", 2)
        val content = formatter.format(ExportSelection.All, listOf(dayOne, dayTwo), zone).textContent()
        assertTrue(content.indexOf("7 августа 2026") < content.indexOf("5 августа 2026"))
    }

    @Test
    fun dayExportTitleAndOrdering() {
        val epochDay = LocalDate.of(2026, 8, 7).toEpochDay()
        val entries = listOf(
            entry(1, epochMillis(2026, 8, 7, 18, 0), "Later", "Later answer", 2),
            entry(2, epochMillis(2026, 8, 7, 11, 0), "Earlier", "Earlier answer", 1),
        )
        val document = formatter.format(ExportSelection.Day(epochDay), entries, zone)
        assertEquals("praktika-day-2026-08-07.md", document.suggestedFileName)
        val content = document.textContent()
        assertTrue(content.startsWith("# Ответы за 7 августа 2026"))
        assertTrue(content.indexOf("Later") < content.indexOf("Earlier"))
    }

    @Test
    fun rangeExportIncludesPeriodHeader() {
        val start = LocalDate.of(2026, 8, 1).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        val content = formatter.format(
            ExportSelection.Range(start, end),
            listOf(entry(1, epochMillis(2026, 8, 3, 12, 0), "Q", "A", 1)),
            zone,
        ).textContent()
        assertTrue(content.contains("# Ответы за период"))
        assertTrue(content.contains("1 августа 2026 — 7 августа 2026"))
    }

    @Test
    fun questionHistoryOldestFirst() {
        val entries = listOf(
            entry(2, epochMillis(2026, 8, 7, 18, 42), "Same Q", "Newer", 2),
            entry(1, epochMillis(2026, 8, 1, 11, 3), "Same Q", "Older", 1),
        )
        val content = formatter.format(ExportSelection.Question(1), entries, zone).textContent()
        assertTrue(content.contains("## Вопрос"))
        assertTrue(content.contains("Same Q"))
        assertTrue(content.indexOf("Цикл 1") < content.indexOf("Цикл 2"))
        assertTrue(content.indexOf("Older") < content.indexOf("Newer"))
    }

    @Test
    fun questionHistoryDifferentSnapshotsPerEntry() {
        val entries = listOf(
            entry(1, epochMillis(2026, 8, 1, 11, 0), "Snapshot A", "Answer A", 1),
            entry(2, epochMillis(2026, 8, 7, 18, 0), "Snapshot B", "Answer B", 2),
        )
        val content = formatter.format(ExportSelection.Question(1), entries, zone).textContent()
        assertTrue(content.contains("Snapshot A"))
        assertTrue(content.contains("Snapshot B"))
        assertTrue(content.indexOf("**Вопрос**") >= 0)
    }

    @Test
    fun multilineCyrillicEmojiAndLongTextPreserved() {
        val longAnswer = buildString {
            repeat(50) { append("Длинный ответ $it. ") }
            append("\n\nВторая строка 🎉")
        }
        val content = formatter.format(
            ExportSelection.All,
            listOf(entry(1, epochMillis(2026, 8, 7, 12, 0), "Q", longAnswer, 1)),
            zone,
        ).textContent()
        assertTrue(content.contains("Длинный ответ 49."))
        assertTrue(content.contains("Вторая строка 🎉"))
    }

    @Test
    fun structuralMarkdownInAnswerEscaped() {
        val content = formatter.format(
            ExportSelection.All,
            listOf(
                entry(
                    1,
                    epochMillis(2026, 8, 7, 12, 0),
                    "Q",
                    "# не заголовок\n- не список",
                    1,
                ),
            ),
            zone,
        ).textContent()
        assertTrue(content.contains("\\# не заголовок"))
        assertTrue(content.contains("\\- не список"))
    }

    @Test
    fun deterministicOutput() {
        val entries = listOf(entry(1, epochMillis(2026, 8, 7, 12, 0), "Q", "A", 1))
        val first = formatter.format(ExportSelection.All, entries, zone).bytes
        val second = formatter.format(ExportSelection.All, entries, zone).bytes
        assertArrayEquals(first, second)
    }

    private fun ExportDocument.textContent(): String = String(bytes, Charsets.UTF_8)

    private fun entry(
        answerId: Long,
        at: Long,
        questionText: String,
        answerText: String,
        cycleNumber: Int,
    ) = ArchiveEntry(
        answerId = answerId,
        occurrenceId = answerId,
        questionId = 1,
        questionText = questionText,
        answerText = answerText,
        answeredAtEpochMillis = at,
        plannedAtEpochMillis = at - 1_000,
        cycleNumber = cycleNumber,
        cyclePosition = cycleNumber,
    )

    private fun epochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
