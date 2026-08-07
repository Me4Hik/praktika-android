// 05.08.2026 Answer Save cursor by Me4Hik START - JVM tests AnswerViewModel
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedException
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedReason
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.AnswerReadResult
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
class AnswerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var readRepository: AnswerViewModelTestSupport.FakeAnswerReadRepository
    private lateinit var saveCommand: AnswerViewModelTestSupport.RecordingSaveAnswerCommand
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: AnswerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        readRepository = AnswerViewModelTestSupport.FakeAnswerReadRepository()
        saveCommand = AnswerViewModelTestSupport.RecordingSaveAnswerCommand()
        savedStateHandle = SavedStateHandle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = AnswerViewModel(
            occurrenceId = AnswerViewModelTestSupport.OCCURRENCE_ID,
            readRepository = readRepository,
            saveAnswerCommand = saveCommand,
            savedStateHandle = savedStateHandle,
            commandDispatcher = testDispatcher,
        )
    }

    @Test
    fun loadingThenInteractiveForAvailableCurrent() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Interactive
        assertEquals("Question 1 text", state.questionText)
        assertFalse(state.canSave)
    }

    @Test
    fun draftUpdateEnablesSave() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Да")
        val state = viewModel.uiState.value as AnswerUiState.Interactive
        assertEquals("Да", state.draftText)
        assertTrue(state.canSave)
    }

    @Test
    fun whitespaceDisablesSave() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("   ")
        val state = viewModel.uiState.value as AnswerUiState.Interactive
        assertFalse(state.canSave)
    }

    @Test
    fun draftRestoredFromSavedStateHandle() = runTest {
        savedStateHandle[AnswerSavedStateKeys.DRAFT_TEXT] = "Restored"
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Interactive
        assertEquals("Restored", state.draftText)
    }

    @Test
    fun saveSuccessClearsDraftAndEmitsNavigationOnce() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        val events = mutableListOf<AnswerNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(1, saveCommand.invocations)
        assertEquals("", savedStateHandle.get<String>(AnswerSavedStateKeys.DRAFT_TEXT))
        assertEquals(1, events.size)
        assertEquals(
            AnswerNavigationEvent.ReturnHomeAfterSave(historyOfferQuestionId = null),
            events.single(),
        )
        job.cancel()
    }

    @Test
    fun doubleClickInvokesCommandOnce() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        viewModel.saveAnswer()
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(1, saveCommand.invocations)
    }

    @Test
    fun saveFailurePreservesDraft() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        saveCommand.exception = RuntimeException("db")
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Keep me")
        viewModel.saveAnswer()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Interactive
        assertEquals("Keep me", state.draftText)
        assertEquals(AnswerSaveError.SAVE_FAILED, state.saveError)
    }

    @Test
    fun expectedIdMismatchNavigatesHomeWithoutConfirmation() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        saveCommand.exception = CycleAnswerNotAllowedException(
            CycleAnswerNotAllowedReason.EXPECTED_ID_MISMATCH,
            "stale",
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        val events = mutableListOf<AnswerNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(AnswerNavigationEvent.ReturnHomeAfterStale, events.single())
        job.cancel()
    }

    @Test
    fun pausedBlocksInteractive() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(
                AnswerViewModelTestSupport.availableSnapshot(isPaused = true),
            ),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Blocked
        assertEquals(AnswerBlockedReason.PRACTICE_PAUSED, state.reason)
    }

    @Test
    fun answeredWithoutAnswerIsBlockedNotCorruption() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(
                AnswerViewModelTestSupport.completedSnapshot(QuestionOccurrenceStatus.ANSWERED)
                    .copy(hasExistingAnswer = false),
            ),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Blocked
        assertEquals(AnswerBlockedReason.ALREADY_COMPLETED, state.reason)
    }

    @Test
    fun existingAnswerOnAvailableIsFatal() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(
                AnswerViewModelTestSupport.availableSnapshot(hasExistingAnswer = true),
            ),
        )
        createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AnswerUiState.FatalError)
    }

    @Test
    fun scheduledHidesQuestionText() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.scheduledSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Blocked
        assertEquals(AnswerBlockedReason.NOT_AVAILABLE_YET, state.reason)
        assertEquals(null, state.questionText)
    }

    @Test
    fun cancellationDoesNotBecomeUiError() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        saveCommand.exception = CancellationException("cancel")
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Draft")
        viewModel.saveAnswer()
        advanceUntilIdle()
        val state = viewModel.uiState.value as AnswerUiState.Interactive
        assertEquals("Draft", state.draftText)
        assertEquals(null, state.saveError)
    }

    @Test
    fun readCorruptionShowsFatalError() = runTest {
        readRepository.failWith(CycleCorruptionException("bad"))
        createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AnswerUiState.FatalError)
    }

    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - repeat save navigation events
    @Test
    fun firstAnswerSaveEmitsNoHistoryOffer() = runTest {
        readRepository.hasPriorAnswers = false
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        val events = mutableListOf<AnswerNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(1, readRepository.lastPriorCheckQuestionId)
        assertEquals(
            AnswerNavigationEvent.ReturnHomeAfterSave(historyOfferQuestionId = null),
            events.single(),
        )
        job.cancel()
    }

    @Test
    fun repeatAnswerSaveEmitsHistoryOfferWithQuestionId() = runTest {
        readRepository.hasPriorAnswers = true
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        val events = mutableListOf<AnswerNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(1, readRepository.lastPriorCheckQuestionId)
        assertEquals(
            AnswerNavigationEvent.ReturnHomeAfterSave(historyOfferQuestionId = 1),
            events.single(),
        )
        job.cancel()
    }

    @Test
    fun historyLookupFailureStillSavesWithoutHistoryOffer() = runTest {
        readRepository.hasPriorAnswersException = RuntimeException("lookup failed")
        readRepository.emit(
            AnswerReadResult.Found(AnswerViewModelTestSupport.availableSnapshot()),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        val events = mutableListOf<AnswerNavigationEvent>()
        val job = launch { viewModel.navigation.collect { events.add(it) } }
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(1, saveCommand.invocations)
        assertEquals(
            AnswerNavigationEvent.ReturnHomeAfterSave(historyOfferQuestionId = null),
            events.single(),
        )
        job.cancel()
    }

    @Test
    fun historyCheckUsesQuestionIdNotOccurrenceId() = runTest {
        readRepository.emit(
            AnswerReadResult.Found(
                AnswerViewModelTestSupport.availableSnapshot().let { snapshot ->
                    snapshot.copy(
                        occurrence = snapshot.occurrence!!.copy(
                            occurrenceId = 99L,
                            questionId = 7,
                        ),
                    )
                },
            ),
        )
        createViewModel()
        advanceUntilIdle()
        viewModel.onDraftChanged("Answer")
        viewModel.saveAnswer()
        advanceUntilIdle()
        assertEquals(7, readRepository.lastPriorCheckQuestionId)
    }
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
}
// 05.08.2026 Answer Save cursor by Me4Hik END
