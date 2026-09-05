// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionsViewModel
// PROMPT 119 — list VM observes mixed history feed
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
                sampleAnswerEvent(1, 1_000L, questionId = 1, answerId = 1, questionText = "Q1 old"),
                sampleAnswerEvent(2, 2_000L, questionId = 1, answerId = 2, questionText = "Q1 new"),
                sampleMissedEvent(3, 1_500L, questionId = 2, questionText = "Q2"),
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
    fun deferOnlyQuestionAppearsInList() = runTest {
        repository.emitHistory(
            listOf(sampleDeferredEvent(9, 90, 4_000L, questionId = 7, questionText = "Defer only")),
        )
        val viewModel = ArchiveQuestionsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionsUiState.Content
        assertEquals(1, content.questions.single().deferredCount)
        assertEquals(7, content.questions.single().questionId)
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

            override fun observeHistoryForQuestion(questionId: Int) =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveHistoryEvent>())

            override fun observeAllHistoryEvents() =
                kotlinx.coroutines.flow.flow<List<com.me4hik.praktika.data.read.ArchiveHistoryEvent>> {
                    throw IllegalStateException("boom")
                }
        }
        val viewModel = ArchiveQuestionsViewModel(failingRepository)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveQuestionsUiState.Error)
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
