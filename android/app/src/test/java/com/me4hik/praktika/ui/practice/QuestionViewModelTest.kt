// 05.08.2026 Question And Skip cursor by Me4Hik START - JVM tests QuestionViewModel
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleSkipNotAllowedException
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.QuestionOccurrenceReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var readRepository: QuestionViewModelTestSupport.FakeQuestionReadRepository
    private lateinit var skipCommand: QuestionViewModelTestSupport.RecordingSkipOccurrenceCommand
    private lateinit var viewModel: QuestionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        readRepository = QuestionViewModelTestSupport.FakeQuestionReadRepository()
        skipCommand = QuestionViewModelTestSupport.RecordingSkipOccurrenceCommand()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = QuestionViewModel(
            occurrenceId = QuestionViewModelTestSupport.OCCURRENCE_ID,
            readRepository = readRepository,
            skipOccurrenceCommand = skipCommand,
            commandDispatcher = testDispatcher,
        )
    }

    @Test
    fun loadingThenInteractiveForAvailableCurrent() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is QuestionUiState.Interactive)
        assertEquals("Question 1 text", (state as QuestionUiState.Interactive).question.questionTextSnapshot)
    }

    @Test
    fun scheduledHidesInteractiveActions() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.scheduledSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as QuestionUiState.Blocked
        assertEquals(QuestionBlockedReason.NOT_AVAILABLE_YET, state.reason)
    }

    @Test
    fun pausedBlocksActions() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(
                QuestionViewModelTestSupport.availableSnapshot(isPaused = true),
            ),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as QuestionUiState.Blocked
        assertEquals(QuestionBlockedReason.PRACTICE_PAUSED, state.reason)
    }

    @Test
    fun completedIsBlocked() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(
                QuestionViewModelTestSupport.completedSnapshot(QuestionOccurrenceStatus.SKIPPED_BY_USER),
            ),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as QuestionUiState.Blocked
        assertEquals(QuestionBlockedReason.ALREADY_COMPLETED, state.reason)
    }

    @Test
    fun notCurrentIsBlocked() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(
                QuestionViewModelTestSupport.availableSnapshot(isCurrent = false),
            ),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as QuestionUiState.Blocked
        assertEquals(QuestionBlockedReason.NOT_CURRENT, state.reason)
    }

    @Test
    fun notFoundIsBlocked() = runTest {
        readRepository.emit(QuestionOccurrenceReadResult.Missing)
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as QuestionUiState.Blocked
        assertEquals(QuestionBlockedReason.NOT_FOUND, state.reason)
        assertEquals(null, state.question)
    }

    @Test
    fun openAnswerEventOnlyFromInteractive() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val events = mutableListOf<QuestionNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onAnswerClicked()
        advanceUntilIdle()
        job.cancel()
        assertEquals(1, events.size)
        assertTrue(events.single() is QuestionNavigationEvent.OpenAnswer)
        assertEquals(
            QuestionViewModelTestSupport.OCCURRENCE_ID,
            (events.single() as QuestionNavigationEvent.OpenAnswer).occurrenceId,
        )
    }

    @Test
    fun skipSuccessEmitsReturnHomeOnce() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val events = mutableListOf<QuestionNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onSkipClicked()
        advanceUntilIdle()
        job.cancel()
        assertEquals(QuestionNavigationEvent.ReturnHomeAfterSkip, events.single())
        assertEquals(1, skipCommand.invocations)
        assertEquals(QuestionViewModelTestSupport.OCCURRENCE_ID, skipCommand.lastExpectedId)
    }

    @Test
    fun skipFailureSetsErrorWithoutNavigation() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        skipCommand.exception = CycleSkipNotAllowedException("denied")
        val events = mutableListOf<QuestionNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.onSkipClicked()
        advanceUntilIdle()
        job.cancel()
        assertTrue(events.isEmpty())
        assertEquals(QuestionSkipError.NOT_ALLOWED, viewModel.commandUiState.value.skipError)
        assertFalse(viewModel.commandUiState.value.isSkipping)
    }

    @Test
    fun doubleClickInvokesSkipOnce() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onSkipClicked()
        viewModel.onSkipClicked()
        advanceUntilIdle()
        assertEquals(1, skipCommand.invocations)
    }

    @Test
    fun corruptionMapsToFatalError() = runTest {
        readRepository.failWith(CycleCorruptionException("broken"))
        createViewModel()
        viewModel.retryRead()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is QuestionUiState.FatalError)
        assertEquals(QuestionFatalError.CORRUPTION, (state as QuestionUiState.FatalError).error)
    }

    @Test
    fun blockedStateDoesNotEmitOpenAnswer() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.scheduledSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val events = mutableListOf<QuestionNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.onAnswerClicked()
        advanceUntilIdle()
        job.cancel()
        assertTrue(events.isEmpty())
        assertEquals(0, skipCommand.invocations)
    }

    @Test
    fun cancellationIsNotMappedToUiError() = runTest {
        readRepository.emit(
            QuestionOccurrenceReadResult.Found(QuestionViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        skipCommand.exception = CancellationException("cancelled")
        val job = launch { viewModel.onSkipClicked() }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()
        assertFalse(viewModel.commandUiState.value.isSkipping)
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
