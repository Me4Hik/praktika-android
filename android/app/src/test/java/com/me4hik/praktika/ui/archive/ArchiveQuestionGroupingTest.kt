// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionGrouping
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveQuestionGroupingTest {
    @Test
    fun emptyListReturnsEmptyQuestions() {
        assertTrue(ArchiveQuestionGrouping.groupQuestions(emptyList()).isEmpty())
    }

    @Test
    fun groupsMultipleQuestionsWithCounts() {
        val grouped = ArchiveQuestionGrouping.groupQuestions(
            listOf(
                sampleEntry(answerId = 1, questionId = 1, epoch = 1000L, text = "Q1 old"),
                sampleEntry(answerId = 2, questionId = 1, epoch = 2000L, text = "Q1 new"),
                sampleEntry(answerId = 3, questionId = 2, epoch = 1500L, text = "Q2 only"),
            ),
        )
        assertEquals(2, grouped.size)
        val questionOne = grouped.first { it.questionId == 1 }
        assertEquals(2, questionOne.answerCount)
        assertEquals("Q1 new", questionOne.questionText)
        val questionTwo = grouped.first { it.questionId == 2 }
        assertEquals(1, questionTwo.answerCount)
        assertEquals("Q2 only", questionTwo.questionText)
    }

    @Test
    fun listSnapshotUsesNewestAnswerText() {
        val grouped = ArchiveQuestionGrouping.groupQuestions(
            listOf(
                sampleEntry(answerId = 1, questionId = 5, epoch = 1000L, text = "Snapshot A"),
                sampleEntry(answerId = 2, questionId = 5, epoch = 3000L, text = "Snapshot B"),
                sampleEntry(answerId = 3, questionId = 5, epoch = 2000L, text = "Snapshot C"),
            ),
        )
        assertEquals("Snapshot B", grouped.single().questionText)
    }

    @Test
    fun groupsSortedLatestFirstWithQuestionIdTieBreaker() {
        val grouped = ArchiveQuestionGrouping.groupQuestions(
            listOf(
                sampleEntry(answerId = 1, questionId = 2, epoch = 1000L),
                sampleEntry(answerId = 2, questionId = 1, epoch = 1000L),
                sampleEntry(answerId = 3, questionId = 3, epoch = 3000L),
            ),
        )
        assertEquals(listOf(3, 1, 2), grouped.map { it.questionId })
    }

    private fun sampleEntry(
        answerId: Long,
        questionId: Int,
        epoch: Long,
        text: String = "Question $questionId",
    ) = ArchiveEntry(
        answerId = answerId,
        occurrenceId = answerId,
        questionId = questionId,
        questionText = text,
        answerText = "Answer $answerId",
        answeredAtEpochMillis = epoch,
        plannedAtEpochMillis = epoch - 1_000,
        cycleNumber = 1,
        cyclePosition = 1,
    )
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
