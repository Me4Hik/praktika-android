// 05.08.2026 Stage 6 Boundary cursor by Me4Hik START - подготовка пользовательского запуска (Этап 11)
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - активные Stage 11 tests
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleAlreadyStartedException
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PracticeRootViewModelStage11StartTest {
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

    private fun emitNotStarted() {
        readRepository.emit(PracticeRootViewModelTestSupport.notStartedSnapshot())
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
    }

    private fun createViewModel() {
        viewModel = PracticeRootViewModelTestSupport.createViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            startCommand = startCommand,
            updateCommand = updateCommand,
            commandDispatcher = testDispatcher,
        )
    }

    @Test
    fun startSuccessInvokesCommandOnce() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertEquals(1, startCommand.invocations)
    }

    @Test
    fun doubleClickInvokesCommandOnce() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onStartPracticeClicked()
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        assertEquals(1, startCommand.invocations)
    }

    @Test
    fun alreadyStartedExceptionDoesNotCreateUiError() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        startCommand.exception = CycleAlreadyStartedException("already")
        viewModel.onStartPracticeClicked()
        advanceUntilIdle()
        val state = viewModel.uiState.value as PracticeUiState.NotStarted
        assertNull(state.startError)
    }

    @Test
    fun roomStartedBeforeCommandCompletionMapsToStarted() = runTest(testDispatcher) {
        emitNotStarted()
        createViewModel()
        advanceUntilIdle()
        viewModel.onStartPracticeClicked()
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(status = QuestionOccurrenceStatus.SCHEDULED),
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PracticeUiState.Started)
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik END
