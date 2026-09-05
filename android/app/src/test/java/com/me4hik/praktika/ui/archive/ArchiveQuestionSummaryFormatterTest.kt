// PROMPT 123 — unit tests for ArchiveQuestionSummaryFormatter
package com.me4hik.praktika.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveQuestionSummaryFormatterTest {
    @Test
    fun answerOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 3,
            rejectedCount = 0,
            missedCount = 0,
            deferredCount = 0,
        )
        assertEquals(listOf(ArchiveSummaryCountKind.Answer), segments.map { it.kind })
        assertEquals(3, segments.single().count)
        assertEquals("Ответов: 3", joinWithLabels(segments))
    }

    @Test
    fun missOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 0, 2, 0)
        assertEquals(listOf(ArchiveSummaryCountKind.Missed), segments.map { it.kind })
        assertEquals("Пропусков: 2", joinWithLabels(segments))
    }

    @Test
    fun rejectedOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 1, 0, 0)
        assertEquals(listOf(ArchiveSummaryCountKind.Rejected), segments.map { it.kind })
        assertEquals("Отклонений: 1", joinWithLabels(segments))
    }

    @Test
    fun deferOnly() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 0, 0, 4)
        assertEquals(listOf(ArchiveSummaryCountKind.Deferred), segments.map { it.kind })
        assertEquals("Отложений: 4", joinWithLabels(segments))
    }

    @Test
    fun mixedAllKinds_preservesOrder() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 4,
            rejectedCount = 1,
            missedCount = 2,
            deferredCount = 3,
        )
        assertEquals(
            listOf(
                ArchiveSummaryCountKind.Answer,
                ArchiveSummaryCountKind.Rejected,
                ArchiveSummaryCountKind.Missed,
                ArchiveSummaryCountKind.Deferred,
            ),
            segments.map { it.kind },
        )
        assertEquals(
            "Ответов: 4 · Отклонений: 1 · Пропусков: 2 · Отложений: 3",
            joinWithLabels(segments),
        )
    }

    @Test
    fun zeroCountsOmitted_andEmptyJoinIsEmpty() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(0, 0, 0, 0)
        assertTrue(segments.isEmpty())
        assertEquals("", ArchiveQuestionSummaryFormatter.join(emptyList()))
    }

    @Test
    fun partialZeros_omittedFromJoin() {
        val segments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
            answerCount = 3,
            rejectedCount = 0,
            missedCount = 2,
            deferredCount = 0,
        )
        assertEquals("Ответов: 3 · Пропусков: 2", joinWithLabels(segments))
    }

    private fun joinWithLabels(segments: List<ArchiveSummaryCountSegment>): String {
        return ArchiveQuestionSummaryFormatter.join(
            segments.map { segment ->
                when (segment.kind) {
                    ArchiveSummaryCountKind.Answer -> "Ответов: ${segment.count}"
                    ArchiveSummaryCountKind.Rejected -> "Отклонений: ${segment.count}"
                    ArchiveSummaryCountKind.Missed -> "Пропусков: ${segment.count}"
                    ArchiveSummaryCountKind.Deferred -> "Отложений: ${segment.count}"
                }
            },
        )
    }
}
