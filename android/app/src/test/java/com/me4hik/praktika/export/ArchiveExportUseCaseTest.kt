// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - unit tests ArchiveExportUseCase
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - CSV format coverage
package com.me4hik.praktika.export

import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.ui.archive.ArchiveDayRange
import com.me4hik.praktika.ui.archive.FakeArchiveReadRepository
import com.me4hik.praktika.ui.archive.FixedArchiveZoneIdProvider
import com.me4hik.praktika.ui.archive.sampleArchiveEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArchiveExportUseCaseTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private lateinit var repository: FakeArchiveReadRepository
    private lateinit var useCase: ArchiveExportUseCase

    @Before
    fun setUp() {
        repository = FakeArchiveReadRepository()
        useCase = ArchiveExportUseCase(
            archiveReadRepository = repository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
        )
    }

    @Test
    fun allSelectionReturnsAllAnswersMarkdown() = runTest {
        repository.emit(
            listOf(
                sampleArchiveEntry(1, millis(2026, 8, 5, 10, 0)),
                sampleArchiveEntry(2, millis(2026, 8, 7, 10, 0)),
            ),
        )
        val result = useCase.prepare(ExportSelection.All, ExportFormat.MARKDOWN)
            as ArchiveExportPrepareResult.Ready
        val content = String(result.document.bytes, Charsets.UTF_8)
        assertTrue(content.contains("Answer 1"))
        assertTrue(content.contains("Answer 2"))
        assertEquals("praktika-all.md", result.document.suggestedFileName)
    }

    @Test
    fun allSelectionReturnsCsvWithBom() = runTest {
        repository.emit(listOf(sampleArchiveEntry(1, millis(2026, 8, 7, 10, 0))))
        val result = useCase.prepare(ExportSelection.All, ExportFormat.CSV)
            as ArchiveExportPrepareResult.Ready
        assertEquals("praktika-all.csv", result.document.suggestedFileName)
        assertArrayEquals(CsvArchiveFormatter.UTF8_BOM, result.document.bytes.copyOfRange(0, 3))
    }

    @Test
    fun csvPrepare_cyrillicRoundTripPreservesUtf8BomAndText() = runTest {
        val question = "Вопрос с \"кавычками\", и запятой"
        val answer = "Ответ\nвторая строка"
        repository.emit(
            listOf(
                sampleArchiveEntry(
                    answerId = 9,
                    epochMillis = millis(2026, 9, 6, 12, 0),
                    questionText = question,
                    answerText = answer,
                ),
            ),
        )
        val result = useCase.prepare(ExportSelection.All, ExportFormat.CSV)
            as ArchiveExportPrepareResult.Ready
        assertEquals(CsvArchiveFormatter.MIME_TYPE, result.document.mimeType)
        assertEquals("praktika-all.csv", result.document.suggestedFileName)
        assertArrayEquals(CsvArchiveFormatter.UTF8_BOM, result.document.bytes.copyOfRange(0, 3))
        val payload = result.document.bytes.copyOfRange(3, result.document.bytes.size)
        val text = String(payload, Charsets.UTF_8)
        assertArrayEquals(payload, text.toByteArray(Charsets.UTF_8))
        val parsed = com.me4hik.praktika.export.csv.CsvTestParser.parse(text)
        assertEquals(question, parsed.dataRows.single()[2])
        assertEquals(answer, parsed.dataRows.single()[4])
    }

    @Test
    fun daySelectionFiltersByLocalDate() = runTest {
        val day = LocalDate.of(2026, 8, 7).toEpochDay()
        repository.emit(
            listOf(
                sampleArchiveEntry(1, millis(2026, 8, 7, 10, 0), answerText = "Day seven"),
                sampleArchiveEntry(2, millis(2026, 8, 5, 10, 0), answerText = "Day five"),
            ),
        )
        val result = useCase.prepare(ExportSelection.Day(day), ExportFormat.MARKDOWN)
            as ArchiveExportPrepareResult.Ready
        val content = String(result.document.bytes, Charsets.UTF_8)
        assertTrue(content.contains("Day seven"))
        assertTrue(!content.contains("Day five"))
    }

    @Test
    fun rangeSelectionIncludesStartAndEndDaysInclusive() = runTest {
        val start = LocalDate.of(2026, 8, 5).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        repository.emit(
            listOf(
                sampleArchiveEntry(1, millis(2026, 8, 5, 0, 1), answerText = "Start day"),
                sampleArchiveEntry(2, millis(2026, 8, 7, 23, 59), answerText = "End day"),
                sampleArchiveEntry(3, millis(2026, 8, 8, 0, 1), answerText = "Next day"),
            ),
        )
        val result = useCase.prepare(ExportSelection.Range(start, end), ExportFormat.CSV)
            as ArchiveExportPrepareResult.Ready
        val content = String(result.document.bytes, Charsets.UTF_8)
        assertTrue(content.contains("Start day"))
        assertTrue(content.contains("End day"))
        assertTrue(!content.contains("Next day"))
    }

    @Test
    fun rangeUsesExclusiveEndBoundInternally() {
        val start = LocalDate.of(2026, 8, 7).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        val bounds = ArchivePeriodRange.bounds(start, end, zone)
        val dayBounds = ArchiveDayRange.bounds(start, zone)
        assertEquals(dayBounds.startInclusiveEpochMillis, bounds.startInclusiveEpochMillis)
        assertEquals(dayBounds.endExclusiveEpochMillis, bounds.endExclusiveEpochMillis)
    }

    @Test
    fun questionSelectionFiltersByQuestionId() = runTest {
        repository.emit(
            listOf(
                sampleArchiveEntry(1, millis(2026, 8, 5, 10, 0), questionId = 1, answerText = "Q1"),
                sampleArchiveEntry(2, millis(2026, 8, 6, 10, 0), questionId = 2, answerText = "Q2"),
            ),
        )
        val result = useCase.prepare(ExportSelection.Question(1), ExportFormat.MARKDOWN)
            as ArchiveExportPrepareResult.Ready
        val content = String(result.document.bytes, Charsets.UTF_8)
        assertTrue(content.contains("Q1"))
        assertTrue(!content.contains("Q2"))
    }

    @Test
    fun emptySelectionReturnsNoAnswersForMarkdownAndCsv() = runTest {
        repository.emit(emptyList())
        assertEquals(
            ArchiveExportPrepareResult.NoAnswers,
            useCase.prepare(ExportSelection.All, ExportFormat.MARKDOWN),
        )
        assertEquals(
            ArchiveExportPrepareResult.NoAnswers,
            useCase.prepare(ExportSelection.All, ExportFormat.CSV),
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
