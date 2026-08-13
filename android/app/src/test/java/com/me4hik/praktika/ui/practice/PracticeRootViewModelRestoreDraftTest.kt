package com.me4hik.praktika.ui.practice

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PracticeRootViewModelRestoreDraftTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var readRepository: PracticeRootViewModelTestSupport.FakePracticeReadRepository
    private lateinit var scheduleReadRepository: PracticeRootViewModelTestSupport.FakeScheduleReadRepository
    private lateinit var viewModel: PracticeRootViewModel

    private val seedSchedule = listOf(660, 900, 1140)
    private val restoredSchedule = listOf(900, 660, 1140)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        readRepository = PracticeRootViewModelTestSupport.FakePracticeReadRepository()
        scheduleReadRepository = PracticeRootViewModelTestSupport.FakeScheduleReadRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun discardOnboardingDraftFromPersistedSchedule_overwritesDirtyDraftWithRestoredSchedule() = runTest(testDispatcher) {
        emitNotStarted(seedSchedule)
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        val dirty = viewModel.uiState.value as PracticeUiState.NotStarted
        assertTrue(dirty.isScheduleDirty)

        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(restoredSchedule),
        )
        advanceUntilIdle()
        val stale = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("10:30", stale.slots.first { it.slotIndex == 1 }.timeText)

        viewModel.discardOnboardingDraftFromPersistedSchedule()
        advanceUntilIdle()
        val synced = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("15:00", synced.slots.first { it.slotIndex == 1 }.timeText)
        assertEquals("11:00", synced.slots.first { it.slotIndex == 2 }.timeText)
        assertEquals("19:00", synced.slots.first { it.slotIndex == 3 }.timeText)
        assertFalse(synced.isScheduleDirty)
    }

    @Test
    fun discardOnboardingDraftFromPersistedSchedule_dirtyInvalidDraftRestoredScheduleWins() = runTest(testDispatcher) {
        emitNotStarted(seedSchedule)
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 660)
        viewModel.onSlotTimeChanged(2, 660)
        advanceUntilIdle()
        val invalid = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals(OnboardingScheduleError.DUPLICATE_TIME, invalid.scheduleError)

        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(restoredSchedule),
        )
        advanceUntilIdle()
        viewModel.discardOnboardingDraftFromPersistedSchedule()
        advanceUntilIdle()
        val synced = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("15:00", synced.slots.first { it.slotIndex == 1 }.timeText)
        assertEquals(null, synced.scheduleError)
        assertTrue(synced.isScheduleValid)
    }

    @Test
    fun externalPersistedScheduleChangeWithoutRestoreApi_preservesDirtyDraft() = runTest(testDispatcher) {
        emitNotStarted(seedSchedule)
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()

        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(restoredSchedule),
        )
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("10:30", state.slots.first { it.slotIndex == 1 }.timeText)
        assertTrue(state.isScheduleDirty)
    }

    @Test
    fun discardOnboardingDraftFromPersistedSchedule_noOpWhenPersistedUnavailable() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()
        viewModel.discardOnboardingDraftFromPersistedSchedule()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PracticeUiState.Loading)
    }

    private fun emitNotStarted(scheduleMinutes: List<Int>) {
        readRepository.emit(PracticeRootViewModelTestSupport.notStartedSnapshot())
        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(scheduleMinutes),
        )
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) {
        viewModel = PracticeRootViewModelTestSupport.createViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            savedStateHandle = savedStateHandle,
            commandDispatcher = testDispatcher,
        )
    }
}
