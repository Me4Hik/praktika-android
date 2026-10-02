// PROMPT 119 — unit tests ArchiveHistoryQuestionGrouping
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - grouping over units
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik START - no deferredCount in summary
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
            listOf(sampleMissedUnit(occurrenceId = 10, eventAt = 1_000L, questionId = 3, questionText = "Missed Q")),
        ).single()
        assertEquals(3, summary.questionId)
        assertEquals("Missed Q", summary.questionText)
        assertEquals(0, summary.answerCount)
        assertEquals(0, summary.rejectedCount)
        assertEquals(1, summary.missedCount)
        assertEquals(1_000L, summary.latestEventAtEpochMillis)
    }

    @Test
    fun rejectedOnlySummary() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(sampleRejectedUnit(occurrenceId = 11, eventAt = 2_000L, questionId = 4)),
        ).single()
        assertEquals(1, summary.rejectedCount)
        assertEquals(0, summary.answerCount)
        assertEquals(0, summary.missedCount)
    }

    @Test
    fun answeredWithNestedDefers_summaryIgnoresDeferCount() {
        val unit = sampleAnsweredUnit(
            occurrenceId = 12,
            eventAt = 3_000L,
            questionId = 5,
            deferEvents = listOf(
                sampleDeferDetail(1, 1_000L),
                sampleDeferDetail(2, 2_000L),
                sampleDeferDetail(3, 2_200L),
                sampleDeferDetail(4, 2_400L),
                sampleDeferDetail(5, 2_600L),
            ),
        )
        assertEquals(5, unit.deferCount)
        assertEquals(5, unit.deferEvents.size)

        val summary = ArchiveHistoryQuestionGrouping.summarize(listOf(unit)).single()
        assertEquals(1, summary.answerCount)
        assertEquals(0, summary.rejectedCount)
        assertEquals(0, summary.missedCount)
    }

    @Test
    fun mixedTerminalCountsPerQuestion() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleMissedUnit(
                    occurrenceId = 21,
                    eventAt = 2_000L,
                    questionId = 1,
                    deferEvents = listOf(
                        sampleDeferDetail(1, 1_000L),
                        sampleDeferDetail(2, 1_500L),
                    ),
                ),
                sampleRejectedUnit(occurrenceId = 22, eventAt = 2_500L, questionId = 1),
                sampleAnsweredUnit(occurrenceId = 23, eventAt = 3_000L, questionId = 1, answerId = 100),
            ),
        ).single()
        assertEquals(1, summary.answerCount)
        assertEquals(1, summary.rejectedCount)
        assertEquals(1, summary.missedCount)
        assertEquals(3_000L, summary.latestEventAtEpochMillis)
    }

    // 03.10.2026 Archive fixed numbering cursor by Me4Hik START - cyclePosition ASC contract
    @Test
    fun sortsByCyclePositionAscending_ignoresActivityTimestamps() {
        val summaries = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleMissedUnit(1, 1_000L, questionId = 2),
                sampleAnsweredUnit(2, 3_000L, questionId = 1, answerId = 2),
                sampleRejectedUnit(3, 2_000L, questionId = 3),
            ),
        )
        assertEquals(listOf(1, 2, 3), summaries.map { it.questionId })
        assertEquals(listOf(1, 2, 3), summaries.map { it.cyclePosition })
    }

    @Test
    fun newerActivityOnHigherCyclePositionDoesNotReorderList() {
        val summaries = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleMissedUnit(1, 1_000L, questionId = 3),
                sampleAnsweredUnit(2, 9_999L, questionId = 15, answerId = 2),
                sampleRejectedUnit(3, 2_000L, questionId = 8),
            ),
        )
        assertEquals(listOf(3, 8, 15), summaries.map { it.questionId })
        assertEquals(listOf(3, 8, 15), summaries.map { it.cyclePosition })
        assertEquals(9_999L, summaries.first { it.questionId == 15 }.latestEventAtEpochMillis)
    }

    @Test
    fun tieBreaksEqualCyclePositionByQuestionIdAscending() {
        val summaries = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleMissedUnit(1, 5_000L, questionId = 9, cyclePosition = 4),
                sampleRejectedUnit(2, 5_000L, questionId = 4, cyclePosition = 4),
                sampleAnsweredUnit(3, 5_000L, questionId = 7, answerId = 3, cyclePosition = 4),
            ),
        )
        assertEquals(listOf(4, 7, 9), summaries.map { it.questionId })
        assertEquals(listOf(4, 4, 4), summaries.map { it.cyclePosition })
    }
    // 03.10.2026 Archive fixed numbering cursor by Me4Hik END

    @Test
    fun deletedAnswerCountsAsAnswer() {
        val summary = ArchiveHistoryQuestionGrouping.summarize(
            listOf(
                sampleAnsweredUnit(
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
                sampleAnsweredUnit(51, 3_000L, questionId = 6, answerId = 51, questionText = "New snap"),
                sampleMissedUnit(52, 2_000L, questionId = 6, questionText = "Mid snap"),
            ),
        ).single()
        assertEquals("New snap", summary.questionText)
        assertEquals(6, summary.cyclePosition)
    }
}
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik END
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
