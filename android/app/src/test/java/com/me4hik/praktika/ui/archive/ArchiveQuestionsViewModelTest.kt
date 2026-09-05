// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionsViewModel
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
        repository.emit(emptyList())
        val viewModel = ArchiveQuestionsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveQuestionsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun contentShowsGroupedQuestionsWithCounts() = runTest {
        repository.emit(
            listOf(
                sampleArchiveEntry(1, 1_000L, questionId = 1, questionText = "Q1 old"),
                sampleArchiveEntry(2, 2_000L, questionId = 1, questionText = "Q1 new"),
                sampleArchiveEntry(3, 1_500L, questionId = 2, questionText = "Q2"),
            ),
        )
        val viewModel = ArchiveQuestionsViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionsUiState.Content
        assertEquals(2, content.questions.size)
        val questionOne = content.questions.first { it.questionId == 1 }
        assertEquals(2, questionOne.answerCount)
        assertEquals("Q1 new", questionOne.questionText)
    }

    @Test
    fun repositoryErrorShowsErrorState() = runTest {
        val failingRepository = object : com.me4hik.praktika.data.read.ArchiveReadRepository {
            override fun observeEntries() =
                kotlinx.coroutines.flow.flow<List<com.me4hik.praktika.data.read.ArchiveEntry>> {
                    throw IllegalStateException("boom")
                }

            override fun observeEntriesInRange(
                startInclusiveEpochMillis: Long,
                endExclusiveEpochMillis: Long,
            ) = observeEntries()

            override fun observeEntriesForQuestion(questionId: Int) = observeEntries()

            override fun observeHistoryForQuestion(questionId: Int) =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveHistoryEvent>())

            override fun observeAllHistoryEvents() =
                kotlinx.coroutines.flow.flowOf(emptyList<com.me4hik.praktika.data.read.ArchiveHistoryEvent>())
        }
        val viewModel = ArchiveQuestionsViewModel(failingRepository)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveQuestionsUiState.Error)
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
