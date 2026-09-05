// PROMPT 119 — unit tests ArchiveHistoryQuestionGrouping
package com.me4hik.praktika.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveHistoryQuestionGroupingTest {
    @Test
    fun emptyReturnsEmpty() {
        assertTrue(ArchiveHistoryQuestionGrouping.summarize(emptyList()).isEmpty())
    }

    @Test
    fun missOnlySummary() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(sampleMissedEvent(occurrenceId = 10, eventAt = 1_000L, questionId = 3, questionText = "Missed Q")),
        ).single()
        assertEquals(3, summary.questionId)
        assertEquals("Missed Q", summary.questionText)
        assertEquals(0, summary.answerCount)
        assertEquals(0, summary.rejectedCount)
        assertEquals(1, summary.missedCount)
        assertEquals(0, summary.deferredCount)
        assertEquals(1_000L, summary.latestEventAtEpochMillis)
    }

    @Test
    fun rejectedOnlySummary() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(sampleRejectedEvent(occurrenceId = 11, eventAt = 2_000L, questionId = 4)),
        ).single()
        assertEquals(1, summary.rejectedCount)
        assertEquals(0, summary.answerCount)
        assertEquals(0, summary.missedCount)
        assertEquals(0, summary.deferredCount)
    }

    @Test
    fun deferOnlySummary() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(sampleDeferredEvent(deferEventId = 1, occurrenceId = 12, eventAt = 3_000L, questionId = 5)),
        ).single()
        assertEquals(1, summary.deferredCount)
        assertEquals(0, summary.answerCount)
    }

    @Test
    fun mixedCountsPerQuestion() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleDeferredEvent(1, 20, 1_000L, questionId = 1),
                sampleDeferredEvent(2, 20, 1_500L, questionId = 1),
                sampleMissedEvent(21, 2_000L, questionId = 1),
                sampleRejectedEvent(22, 2_500L, questionId = 1),
                sampleAnswerEvent(23, 3_000L, questionId = 1, answerId = 100),
            ),
        ).single()
        assertEquals(1, summary.answerCount)
        assertEquals(1, summary.rejectedCount)
        assertEquals(1, summary.missedCount)
        assertEquals(2, summary.deferredCount)
        assertEquals(3_000L, summary.latestEventAtEpochMillis)
    }

    @Test
    fun latestActivitySortDescending() {
        val summaries = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleMissedEvent(1, 1_000L, questionId = 2),
                sampleAnswerEvent(2, 3_000L, questionId = 1, answerId = 2),
                sampleDeferredEvent(3, 30, 2_000L, questionId = 3),
            ),
        )
        assertEquals(listOf(1, 3, 2), summaries.map { it.questionId })
    }

    @Test
    fun tieBreaksByQuestionIdAscending() {
        val summaries = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleMissedEvent(1, 5_000L, questionId = 9),
                sampleRejectedEvent(2, 5_000L, questionId = 4),
                sampleDeferredEvent(3, 30, 5_000L, questionId = 7),
            ),
        )
        assertEquals(listOf(4, 7, 9), summaries.map { it.questionId })
    }

    @Test
    fun deletedAnswerCountsAsAnswer() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleAnswerEvent(
                    occurrenceId = 40,
                    eventAt = 4_000L,
                    questionId = 8,
                    answerId = null,
                    answerText = null,
                    questionText = "Deleted body Q",
                ),
            ),
        ).single()
        assertEquals(1, summary.answerCount)
        assertEquals("Deleted body Q", summary.questionText)
    }

    @Test
    fun latestSnapshotTextWins() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleDeferredEvent(1, 50, 1_000L, questionId = 6, questionText = "Old snap"),
                sampleAnswerEvent(51, 3_000L, questionId = 6, answerId = 51, questionText = "New snap"),
                sampleMissedEvent(52, 2_000L, questionId = 6, questionText = "Mid snap"),
            ),
        ).single()
        assertEquals("New snap", summary.questionText)
        assertEquals(6, summary.cyclePosition)
    }
}
