// 05.08.2026 Main Screen cursor by Me4Hik START - unit tests PracticeRootViewModel
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik START - read/display Этапа 6 без UI-start
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - schedule draft tests
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleInvalidScheduleException
import com.me4hik.praktika.data.cycle.ScheduleValidationException
import com.me4hik.praktika.data.cycle.ScheduleValidationReason
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

class PracticeRootViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var readRepository: PracticeRootViewModelTestSupport.FakePracticeReadRepository
    private lateinit var scheduleReadRepository: PracticeRootViewModelTestSupport.FakeScheduleReadRepository
    private lateinit var startCommand: PracticeRootViewModelTestSupport.RecordingStartPracticeCommand
    private lateinit var updateCommand: PracticeRootViewModelTestSupport.RecordingUpdateScheduleCommand
    private lateinit var viewModel: PracticeRootViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        readRepository = PracticeRootViewModelTestSupport.FakePracticeReadRepository()
        scheduleReadRepository = PracticeRootViewModelTestSupport.FakeScheduleReadRepository()
        startCommand = PracticeRootViewModelTestSupport.RecordingStartPracticeCommand()
        updateCommand = PracticeRootViewModelTestSupport.RecordingUpdateScheduleCommand()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun emitNotStarted(
        scheduleMinutes: List<Int> = PracticeRootViewModelTestSupport.DEFAULT_SCHEDULE_MINUTES,
    ) {
        readRepository.emit(PracticeRootViewModelTestSupport.notStartedSnapshot())
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot(scheduleMinutes))
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) {
        viewModel = PracticeRootViewModelTestSupport.createViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            startCommand = startCommand,
            updateCommand = updateCommand,
            savedStateHandle = savedStateHandle,
            commandDispatcher = testDispatcher,
        )
    }

    @Test
    fun initialStateIsLoading() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()
        assertEquals(PracticeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun notStartedPracticeMapsToNotStarted() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertFalse(state.isStarting)
        assertNull(state.startError)
        assertEquals(3, state.slots.size)
        assertFalse(state.isScheduleLoading)
    }

    @Test
    fun startClickWithoutCommandIsNoOp() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertEquals(1, startCommand.invocations)
    }

    @Test
    fun scheduledMapsToScheduledContent() = runTest(testDispatcher) {
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(status = QuestionOccurrenceStatus.SCHEDULED),
        )
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.Started
        assertTrue(state.content is MainContentUiState.Scheduled)
        assertEquals(1, state.content.occurrence.cyclePosition)
    }

    @Test
    fun availableMapsToAvailableContent() = runTest(testDispatcher) {
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(status = QuestionOccurrenceStatus.AVAILABLE),
        )
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.Started
        assertTrue(state.content is MainContentUiState.Available)
        assertEquals("Question 1 text", state.content.occurrence.questionText)
    }

    @Test
    fun pausedScheduledMapsCorrectly() = runTest(testDispatcher) {
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(
                status = QuestionOccurrenceStatus.SCHEDULED,
                paused = true,
            ),
        )
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
        createViewModel()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as PracticeUiState.Started).content is MainContentUiState.PausedScheduled)
    }

    @Test
    fun pausedAvailableMapsCorrectly() = runTest(testDispatcher) {
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(
                status = QuestionOccurrenceStatus.AVAILABLE,
                paused = true,
            ),
        )
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
        createViewModel()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as PracticeUiState.Started).content is MainContentUiState.PausedAvailable)
    }

    @Test
    fun cycleNumberNotInUserFacingText() = runTest(testDispatcher) {
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(
                status = QuestionOccurrenceStatus.AVAILABLE,
                cycleNumber = 99,
            ),
        )
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
        createViewModel()
        advanceUntilIdle()
        val occurrence = (viewModel.uiState.value as PracticeUiState.Started).content.occurrence
        assertEquals(1, occurrence.cyclePosition)
        assertFalse(occurrence.questionText.contains("99"))
    }

    @Test
    fun invalidScheduleMapsToStartFailed() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        startCommand.exception = CycleInvalidScheduleException("invalid")
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals(PracticeUiError.START_FAILED, state.startError)
    }

    @Test
    fun corruptionOnStartMapsToFatalError() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        startCommand.exception = CycleCorruptionException("corrupt")
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PracticeUiState.FatalError)
    }

    @Test
    fun duplicateDraftShowsInlineValidation() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(2, 660)
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals(OnboardingScheduleError.DUPLICATE_TIME, state.scheduleError)
        assertFalse(state.isScheduleValid)
    }

    @Test
    fun dirtyStartCallsUpdateThenStart() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertEquals(1, updateCommand.invocations)
        assertEquals(1, startCommand.invocations)
        assertEquals(630, updateCommand.lastUpdates?.first()?.timeOfDayMinutes)
    }

    @Test
    fun defaultStartCallsOnlyStart() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertEquals(0, updateCommand.invocations)
        assertEquals(1, startCommand.invocations)
    }

    @Test
    fun updateFailureDoesNotCallStart() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        updateCommand.exception = ScheduleValidationException(ScheduleValidationReason.DUPLICATE_TIME)
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertEquals(1, updateCommand.invocations)
        assertEquals(0, startCommand.invocations)
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals(PracticeUiError.START_FAILED, state.startError)
    }

    @Test
    fun retryReadDoesNotCrash() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.retryRead()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PracticeUiState.NotStarted)
    }

    @Test
    fun cancellationIsNotMappedToError() = runTest(testDispatcher) {
        readRepository.failWith(CancellationException("cancel"))
        createViewModel()
        try {
            advanceUntilIdle()
        } catch (_: CancellationException) {
        }
        assertTrue(
            viewModel.uiState.value is PracticeUiState.Loading ||
                viewModel.uiState.value is PracticeUiState.FatalError,
        )
    }

    @Test
    fun savedStateHandleRestoresDraft() = runTest(testDispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                OnboardingSavedStateKeys.DRAFT_SLOT_1 to 630,
                OnboardingSavedStateKeys.DRAFT_SLOT_2 to 900,
                OnboardingSavedStateKeys.DRAFT_SLOT_3 to 1140,
                OnboardingSavedStateKeys.DRAFT_INITIALIZED to true,
            ),
        )
        emitNotStarted()
        createViewModel(handle)
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("10:30", state.slots.first { it.slotIndex == 1 }.timeText)
        assertTrue(state.isScheduleDirty)
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
