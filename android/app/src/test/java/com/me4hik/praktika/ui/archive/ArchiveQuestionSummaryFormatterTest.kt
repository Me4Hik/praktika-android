// PROMPT 123 — unit tests for ArchiveQuestionSummaryFormatter
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik START - no Deferred segment
package com.me4hik.praktika.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveQuestionSummaryFormatterTest {
    @Test
    fun answerOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 1,
            rejectedCount = 0,
            missedCount = 0,
        )
        assertEquals(listOf(ArchiveSummaryCountKind.Answer), segments.map { it.kind })
        assertEquals("Ответов: 1", format(segments))
    }

    @Test
    fun answeredWithDeferEvents_stillAnswerOnly() {
        // Nested deferCount lives on ArchiveOccurrenceUnit; formatter has no defer input.
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 1,
            rejectedCount = 0,
            missedCount = 0,
        )
        assertEquals(listOf(ArchiveSummaryCountKind.Answer), segments.map { it.kind })
        assertEquals("Ответов: 1", format(segments))
        assertTrue(segments.none { it.kind.name == "Deferred" })
    }

    @Test
    fun missedOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 0, 2)
        assertEquals(listOf(ArchiveSummaryCountKind.Missed), segments.map { it.kind })
        assertEquals("Пропусков: 2", format(segments))
    }

    @Test
    fun rejectedOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 1, 0)
        assertEquals(listOf(ArchiveSummaryCountKind.Rejected), segments.map { it.kind })
        assertEquals("Отклонений: 1", format(segments))
    }

    @Test
    fun allTerminalKinds_fixedOrder() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 2,
            rejectedCount = 1,
            missedCount = 4,
        )
        assertEquals(
            listOf(
                ArchiveSummaryCountKind.Answer,
                ArchiveSummaryCountKind.Rejected,
                ArchiveSummaryCountKind.Missed,
            ),
            segments.map { it.kind },
        )
        assertEquals("Ответов: 2 · Отклонений: 1 · Пропусков: 4", format(segments))
    }

    @Test
    fun allZero_empty() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 0, 0)
        assertTrue(segments.isEmpty())
        assertEquals("", ArchiveQuestionSummaryFormatter.join(emptyList()))
    }

    @Test
    fun skipsZeroSegments_noExtraSeparators() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 1,
            rejectedCount = 0,
            missedCount = 2,
        )
        assertEquals("Ответов: 1 · Пропусков: 2", format(segments))
    }

    private fun format(segments: List<ArchiveSummaryCountSegment>): String {
        return ArchiveQuestionSummaryFormatter.join(
            segments.map { segment ->
                when (segment.kind) {
                    ArchiveSummaryCountKind.Answer -> "Ответов: ${segment.count}"
                    ArchiveSummaryCountKind.Rejected -> "Отклонений: ${segment.count}"
                    ArchiveSummaryCountKind.Missed -> "Пропусков: ${segment.count}"
                }
            },
        )
    }
}
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik END
