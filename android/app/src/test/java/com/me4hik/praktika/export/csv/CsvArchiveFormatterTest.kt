// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - unit tests CsvArchiveFormatter
package com.me4hik.praktika.export.csv

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvArchiveFormatterTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val formatter = CsvArchiveFormatter()

    @Test
    fun headerColumnsBomAndDialect() {
        val document = formatter.format(
            selection = ExportSelection.All,
            entries = listOf(sampleEntry(answerId = 1)),
            zoneId = zone,
        )
        assertEquals("praktika-all.csv", document.suggestedFileName)
        assertEquals(CsvArchiveFormatter.MIME_TYPE, document.mimeType)
        assertArrayEquals(CsvArchiveFormatter.UTF8_BOM, document.bytes.copyOfRange(0, 3))
        val text = document.textWithoutBom()
        assertTrue(text.startsWith(CsvArchiveFormatter.HEADER))
        assertTrue(text.contains("\r\n"))
        val parsed = CsvTestParser.parse(text)
        assertEquals(
            listOf(
                "answer_id",
                "question_id",
                "question",
                "topic",
                "answer",
                "date",
                "time",
                "cycle_number",
            ),
            parsed.header,
        )
        assertEquals(8, parsed.header.size)
    }

    @Test
    fun rowMappingAndEmptyTopic() {
        val entry = sampleEntry(
            answerId = 15,
            questionId = 3,
            questionText = "Historical snapshot question",
            answerText = "Тёплый красный",
            at = epochMillis(2026, 7, 3, 14, 3),
            cycleNumber = 1,
        )
        val parsed = parseSingleDataRow(
            formatter.format(ExportSelection.All, listOf(entry), zone).textWithoutBom(),
        )
        assertEquals(
            listOf("15", "3", "Historical snapshot question", "", "Тёплый красный", "2026-07-03", "14:03", "1"),
            parsed,
        )
    }

    @Test
    fun escapingQuotesCommasMultilineAndSpaces() {
        val entry = sampleEntry(
            answerText = " hello, world ",
            questionText = "Он сказал \"да\"",
            at = epochMillis(2026, 8, 7, 18, 0),
        )
        val multilineEntry = sampleEntry(
            answerId = 2,
            answerText = "строка 1\nстрока 2",
            questionText = "Q2",
            at = epochMillis(2026, 8, 7, 12, 0),
        )
        val text = formatter.format(
            ExportSelection.All,
            listOf(entry, multilineEntry),
            zoneId = zone,
        ).textWithoutBom()
        assertTrue(text.contains("\" hello, world \""))
        assertTrue(text.contains("\"Он сказал \"\"да\"\"\""))
        assertTrue(text.contains("\"строка 1\nстрока 2\""))
        val parsed = CsvTestParser.parse(text)
        assertEquals(" hello, world ", parsed.dataRows[0][4])
        assertEquals("Он сказал \"да\"", parsed.dataRows[0][2])
        assertEquals("строка 1\nстрока 2", parsed.dataRows[1][4])
    }

    @Test
    fun cyrillicEmojiLongTextAndFormulaLikeAnswerUnchanged() {
        val entry = sampleEntry(
            answerText = "=1+1\nemoji 🎉",
            questionText = "  вопрос  ",
        )
        val parsed = parseSingleDataRow(
            formatter.format(ExportSelection.All, listOf(entry), zone).textWithoutBom(),
        )
        assertEquals("=1+1\nemoji 🎉", parsed[4])
        assertEquals("  вопрос  ", parsed[2])
        assertEquals("", parsed[3])
    }

    @Test
    fun allScopeOrdersNewestFirst() {
        val newer = sampleEntry(answerId = 2, at = epochMillis(2026, 8, 7, 18, 0), answerText = "Newer")
        val older = sampleEntry(answerId = 1, at = epochMillis(2026, 8, 5, 10, 0), answerText = "Older")
        val parsed = CsvTestParser.parse(
            formatter.format(ExportSelection.All, listOf(older, newer), zone).textWithoutBom(),
        )
        assertEquals("Newer", parsed.dataRows[0][4])
        assertEquals("Older", parsed.dataRows[1][4])
    }

    @Test
    fun questionScopeOrdersOldestFirst() {
        val newer = sampleEntry(answerId = 2, at = epochMillis(2026, 8, 7, 18, 0), answerText = "Newer")
        val older = sampleEntry(answerId = 1, at = epochMillis(2026, 8, 1, 11, 3), answerText = "Older")
        val parsed = CsvTestParser.parse(
            formatter.format(ExportSelection.Question(1), listOf(newer, older), zone).textWithoutBom(),
        )
        assertEquals("Older", parsed.dataRows[0][4])
        assertEquals("Newer", parsed.dataRows[1][4])
    }

    @Test
    fun roundTripPreservesComplexValues() {
        val entry = sampleEntry(
            answerText = "alpha,beta\n\"gamma\" 🎉",
            questionText = "Q, \"quoted\"",
        )
        val text = formatter.format(ExportSelection.All, listOf(entry), zone).textWithoutBom()
        val parsed = CsvTestParser.parse(text)
        parsed.dataRows.forEach { row ->
            assertEquals(8, row.size)
        }
        assertEquals(entry.questionText, parsed.dataRows.single()[2])
        assertEquals(entry.answerText, parsed.dataRows.single()[4])
    }

    @Test
    fun deterministicOutput() {
        val entries = listOf(sampleEntry(answerId = 1))
        val first = formatter.format(ExportSelection.All, entries, zone).bytes
        val second = formatter.format(ExportSelection.All, entries, zone).bytes
        assertArrayEquals(first, second)
    }

    @Test
    fun escapeFieldUnitCases() {
        assertEquals("hello", CsvArchiveFormatter.escapeField("hello"))
        assertEquals("\"hello, world\"", CsvArchiveFormatter.escapeField("hello, world"))
        assertEquals("\"Он сказал \"\"да\"\"\"", CsvArchiveFormatter.escapeField("Он сказал \"да\""))
    }

    private fun parseSingleDataRow(text: String): List<String> {
        val parsed = CsvTestParser.parse(text)
        assertEquals("CSV text:\n$text", 1, parsed.dataRows.size)
        assertEquals("CSV text:\n$text", 8, parsed.dataRows.single().size)
        return parsed.dataRows.single()
    }

    private fun sampleEntry(
        answerId: Long = 1,
        questionId: Int = 1,
        questionText: String = "Question $answerId",
        answerText: String = "Answer $answerId",
        at: Long = epochMillis(2026, 8, 7, 12, 0),
        cycleNumber: Int = 1,
    ) = ArchiveEntry(
        answerId = answerId,
        occurrenceId = answerId,
        questionId = questionId,
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

    private fun com.me4hik.praktika.export.ExportDocument.textWithoutBom(): String {
        val payload = if (bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(CsvArchiveFormatter.UTF8_BOM)) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return String(payload, Charsets.UTF_8)
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
