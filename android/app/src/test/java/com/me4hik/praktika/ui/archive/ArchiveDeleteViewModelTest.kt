// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - unit tests delete flow archive ViewModels
package com.me4hik.praktika.ui.archive

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveDeleteViewModelTest {
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
    fun requestDeleteOpensDialogState() = runTest {
        val viewModel = createDayViewModel(LocalDate.of(2026, 8, 7).toEpochDay())
        viewModel.requestDelete(42L)
        assertEquals(42L, viewModel.deleteUiState.value.pendingDeleteAnswerId)
        assertTrue(viewModel.deleteUiState.value.isDialogVisible)
    }

    @Test
    fun cancelDeleteDoesNotCallRepository() = runTest {
        val viewModel = createDayViewModel(LocalDate.of(2026, 8, 7).toEpochDay())
        viewModel.requestDelete(42L)
        viewModel.cancelDelete()
        assertFalse(viewModel.deleteUiState.value.isDialogVisible)
        assertEquals(0, deleteRepository.deleteCallCount)
    }

    @Test
    fun confirmDeleteCallsRepositoryOnceAndClearsPendingState() = runTest {
        val viewModel = createHistoryViewModel(questionId = 1)
        viewModel.requestDelete(7L)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(listOf(7L), deleteRepository.deletedAnswerIds)
        assertEquals(1, deleteRepository.deleteCallCount)
        assertFalse(viewModel.deleteUiState.value.isDialogVisible)
        assertNull(viewModel.deleteUiState.value.pendingDeleteAnswerId)
    }

    @Test
    fun doubleConfirmDeletesOnce() = runTest {
        val viewModel = createDayViewModel(LocalDate.of(2026, 8, 7).toEpochDay())
        viewModel.requestDelete(5L)
        viewModel.confirmDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(1, deleteRepository.deleteCallCount)
        assertEquals(listOf(5L), deleteRepository.deletedAnswerIds)
    }

    @Test
    fun deleteFailureSetsErrorState() = runTest {
        deleteRepository.failNextDelete = true
        val viewModel = createHistoryViewModel(questionId = 1)
        viewModel.requestDelete(9L)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(ArchiveDeleteController.DELETE_FAILED, viewModel.deleteUiState.value.deleteError)
        assertEquals(9L, viewModel.deleteUiState.value.pendingDeleteAnswerId)
    }

    private fun createDayViewModel(epochDay: Long): ArchiveDayViewModel {
        return ArchiveDayViewModel(
            archiveReadRepository = repository,
            answerDeleteRepository = deleteRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
            epochDay = epochDay,
            ioDispatcher = dispatcher,
        )
    }

    private fun createHistoryViewModel(questionId: Int): ArchiveQuestionHistoryViewModel {
        return ArchiveQuestionHistoryViewModel(
            archiveReadRepository = repository,
            answerDeleteRepository = deleteRepository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            displayFormatter = ArchiveDisplayFormatter(),
            questionId = questionId,
            ioDispatcher = dispatcher,
        )
    }

    private fun epoch(day: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
