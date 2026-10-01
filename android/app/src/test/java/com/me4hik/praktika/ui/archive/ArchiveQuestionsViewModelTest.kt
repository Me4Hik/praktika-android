// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionsViewModel
// PROMPT 119 — list VM observes mixed history feed
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - list VM from units
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik START - no deferredCount in list
package com.me4hik.praktika.ui.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveQuestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveReadRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeArchiveReadRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyArchiveShowsEmptyState() = runTest {
        repository.emitHistory(emptyList())
        val viewModel = ArchiveQuestionsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveQuestionsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun contentShowsGroupedQuestionsWithMixedCounts() = runTest {
        repository.emitHistory(
            listOf(
                sampleAnsweredUnit(1, 1_000L, questionId = 1, answerId = 1, questionText = "Q1 old"),
                sampleAnsweredUnit(2, 2_000L, questionId = 1, answerId = 2, questionText = "Q1 new"),
                sampleMissedUnit(3, 1_500L, questionId = 2, questionText = "Q2"),
            ),
        )
        val viewModel = ArchiveQuestionsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionsUiState.Content
        assertEquals(2, content.questions.size)
        assertEquals(1, content.questions[0].questionId)
        val questionOne = content.questions.first { it.questionId == 1 }
        assertEquals(2, questionOne.answerCount)
        assertEquals("Q1 new", questionOne.questionText)
        val questionTwo = content.questions.first { it.questionId == 2 }
        assertEquals(1, questionTwo.missedCount)
        assertEquals(0, questionTwo.answerCount)
    }

    @Test
    fun answeredWithNestedDefers_listShowsOnlyAnswerCount() = runTest {
        val unit = sampleAnsweredUnit(
            occurrenceId = 90,
            eventAt = 4_000L,
            questionId = 7,
            questionText = "With defers",
            deferEvents = listOf(
                sampleDeferDetail(1, 1_000L),
                sampleDeferDetail(2, 2_000L),
                sampleDeferDetail(3, 3_000L),
                sampleDeferDetail(4, 3_200L),
                sampleDeferDetail(5, 3_400L),
            ),
        )
        assertEquals(5, unit.deferCount)

        repository.emitHistory(listOf(unit))
        val viewModel = ArchiveQuestionsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionsUiState.Content
        val item = content.questions.single()
        assertEquals(1, item.answerCount)
        assertEquals(0, item.rejectedCount)
        assertEquals(0, item.missedCount)
        assertEquals(7, item.questionId)
    }

    @Test
    fun repositoryErrorShowsErrorState() = runTest {
        val failingRepository = object : com.me4hik.praktika.data.read.ArchiveReadRepository {
            override fun observeEntries() =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveEntry>())

            override fun observeEntriesInRange(
                startInclusiveEpochMillis: Long,
                endExclusiveEpochMillis: Long,
            ) = observeEntries()

            override fun observeEntriesForQuestion(questionId: Int) = observeEntries()

            override fun observeOccurrenceHistoryForQuestion(questionId: Int) =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveOccurrenceUnit>())

            override fun observeAllOccurrenceHistory() =
                kotlinx.coroutines.flow.flow<List<com.me4hik.praktika.data.read.ArchiveOccurrenceUnit>> {
                    throw IllegalStateException("boom")
                }
        }
        val viewModel = ArchiveQuestionsViewModel(failingRepository)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveQuestionsUiState.Error)
    }
}
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik END
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
