// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - unit tests ArchiveQuestionHistoryViewModel
package com.me4hik.praktika.ui.archive

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
class ArchiveQuestionHistoryViewModelTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeArchiveReadRepository
    private lateinit var deleteRepository: FakeAnswerDeleteRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeArchiveReadRepository()
        deleteRepository = FakeAnswerDeleteRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectedQuestionHistoryPreservesOrderDateTimeAndCycle() = runTest {
        val day = LocalDate.of(2026, 8, 7)
        repository.emit(
            listOf(
                sampleArchiveEntry(
                    answerId = 2,
                    epochMillis = epoch(day, 18, 42),
                    questionId = 1,
                    questionText = "Snapshot B",
                    answerText = "Answer 2",
                    cycleNumber = 2,
                ),
                sampleArchiveEntry(
                    answerId = 1,
                    epochMillis = epoch(day, 10, 0),
                    questionId = 1,
                    questionText = "Snapshot A",
                    answerText = "Answer 1",
                    cycleNumber = 1,
                ),
                sampleArchiveEntry(
                    answerId = 3,
                    epochMillis = epoch(day, 12, 0),
                    questionId = 2,
                    questionText = "Other",
                    answerText = "Other answer",
                    cycleNumber = 1,
                ),
            ),
        )
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as ArchiveQuestionHistoryUiState.Content
        assertEquals(2, content.entries.size)
        assertEquals(1L, content.entries[0].answerId)
        assertEquals(2L, content.entries[1].answerId)
        assertEquals("Snapshot A", content.entries[0].questionText)
        assertEquals("Snapshot B", content.entries[1].questionText)
        assertEquals("Answer 1", content.entries[0].answerText)
        assertTrue(content.entries[0].dateTimeText.contains("10:00"))
        assertEquals(1, content.entries[0].cycleNumber)
        assertEquals(2, content.entries[1].cycleNumber)
    }

    @Test
    fun invalidQuestionIdShowsError() = runTest {
        val viewModel = createViewModel(questionId = null)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ArchiveQuestionHistoryUiState.Error)
    }

    @Test
    fun emptyQuestionHistoryShowsEmptyState() = runTest {
        repository.emit(emptyList())
        val viewModel = createViewModel(questionId = 1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ArchiveQuestionHistoryUiState.Empty, viewModel.uiState.value)
    }

    private fun createViewModel(questionId: Int?): ArchiveQuestionHistoryViewModel {
        return ArchiveQuestionHistoryViewModel(
            archiveReadRepository = repository,
            answerDeleteRepository = deleteRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
            questionId = questionId,
        )
    }

    private fun epoch(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
