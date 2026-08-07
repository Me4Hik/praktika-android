// 05.08.2026 Answer Save cursor by Me4Hik START - ViewModel экрана ответа
package com.me4hik.praktika.ui.practice

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.cycle.CycleAlreadyPausedException
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedException
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedReason
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleNotStartedException
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.AnswerOccurrenceReadModel
import com.me4hik.praktika.data.read.AnswerReadRepository
import com.me4hik.praktika.data.read.AnswerReadResult
import com.me4hik.praktika.data.read.AnswerReadSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerViewModel(
    private val occurrenceId: Long,
    private val readRepository: AnswerReadRepository,
    private val saveAnswerCommand: SaveAnswerCommand,
    private val savedStateHandle: SavedStateHandle,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val readGeneration = MutableStateFlow(0)
    private val navigationEvents = MutableSharedFlow<AnswerNavigationEvent>(extraBufferCapacity = 1)
    private var isSaving = false

    private val _uiState = MutableStateFlow<AnswerUiState>(AnswerUiState.Loading)
    val uiState: StateFlow<AnswerUiState> = _uiState.asStateFlow()

    val navigation: SharedFlow<AnswerNavigationEvent> = navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            readGeneration
                .flatMapLatest { observeReadResults() }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun draftText(): String {
        return savedStateHandle.get<String>(AnswerSavedStateKeys.DRAFT_TEXT).orEmpty()
    }

    fun onDraftChanged(text: String) {
        savedStateHandle[AnswerSavedStateKeys.DRAFT_TEXT] = text
        val current = _uiState.value
        if (current is AnswerUiState.Interactive && !current.isSaving) {
            _uiState.value = current.copy(
                draftText = text,
                canSave = text.isNotBlank(),
                saveError = null,
            )
        }
    }

    fun retryRead() {
        readGeneration.value += 1
    }

    fun saveAnswer() {
        if (isSaving) {
            return
        }
        val state = _uiState.value
        if (state !is AnswerUiState.Interactive) {
            return
        }
        val draft = state.draftText
        if (draft.isBlank()) {
            _uiState.value = state.copy(saveError = AnswerSaveError.BLANK)
            return
        }

        isSaving = true
        _uiState.value = state.copy(
            isSaving = true,
            canSave = false,
            saveError = null,
        )

        viewModelScope.launch {
            try {
                // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - prior answers UX check
                val questionId = state.questionId
                val hasPriorAnswers = try {
                    withContext(commandDispatcher) {
                        readRepository.hasPriorAnswersForQuestion(questionId)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.w(TAG, "Prior answer lookup failed for questionId=$questionId", exception)
                    false
                }
                // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
                withContext(commandDispatcher) {
                    saveAnswerCommand.save(
                        expectedOccurrenceId = occurrenceId,
                        answerText = draft,
                    )
                }
                savedStateHandle[AnswerSavedStateKeys.DRAFT_TEXT] = ""
                // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - history offer event
                navigationEvents.tryEmit(
                    AnswerNavigationEvent.ReturnHomeAfterSave(
                        historyOfferQuestionId = questionId.takeIf { hasPriorAnswers },
                    ),
                )
                // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
            } catch (exception: CycleAnswerNotAllowedException) {
                handleSaveNotAllowed(exception)
            } catch (_: CycleAlreadyPausedException) {
                _uiState.value = AnswerUiState.Blocked(
                    questionText = state.questionText,
                    reason = AnswerBlockedReason.PRACTICE_PAUSED,
                )
            } catch (_: CycleNotStartedException) {
                _uiState.value = AnswerUiState.Blocked(
                    questionText = null,
                    reason = AnswerBlockedReason.NOT_FOUND,
                )
            } catch (exception: CancellationException) {
                restoreInteractiveAfterFailure(state, draft, saveError = null)
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Save failed", exception)
                restoreInteractiveAfterFailure(
                    state = state,
                    draft = draft,
                    saveError = AnswerSaveError.SAVE_FAILED,
                )
            } finally {
                isSaving = false
            }
        }
    }

    private fun handleSaveNotAllowed(exception: CycleAnswerNotAllowedException) {
        when (exception.reason) {
            CycleAnswerNotAllowedReason.BLANK -> {
                updateInteractiveSaveError(AnswerSaveError.BLANK)
            }
            CycleAnswerNotAllowedReason.EXPECTED_ID_MISMATCH,
            CycleAnswerNotAllowedReason.NO_AVAILABLE_OCCURRENCE,
            CycleAnswerNotAllowedReason.WINDOW_EXPIRED,
            CycleAnswerNotAllowedReason.ALREADY_COMPLETED,
            -> {
                _uiState.value = AnswerUiState.Blocked(
                    questionText = currentQuestionText(),
                    reason = AnswerBlockedReason.NOT_CURRENT,
                )
                navigationEvents.tryEmit(AnswerNavigationEvent.ReturnHomeAfterStale)
            }
            CycleAnswerNotAllowedReason.ANSWER_ALREADY_EXISTS -> {
                _uiState.value = AnswerUiState.FatalError(AnswerFatalError.CORRUPTION)
            }
        }
    }

    private fun updateInteractiveSaveError(error: AnswerSaveError) {
        val current = _uiState.value
        if (current is AnswerUiState.Interactive) {
            _uiState.value = current.copy(
                isSaving = false,
                canSave = current.draftText.isNotBlank(),
                saveError = error,
            )
        }
    }

    private fun restoreInteractiveAfterFailure(
        state: AnswerUiState.Interactive,
        draft: String,
        saveError: AnswerSaveError? = AnswerSaveError.SAVE_FAILED,
    ) {
        _uiState.value = state.copy(
            draftText = draft,
            isSaving = false,
            canSave = draft.isNotBlank(),
            saveError = saveError,
        )
    }

    private fun currentQuestionText(): String? {
        return when (val state = _uiState.value) {
            is AnswerUiState.Interactive -> state.questionText
            is AnswerUiState.Blocked -> state.questionText
            else -> null
        }
    }

    private fun observeReadResults() = flow {
        emit(AnswerUiState.Loading)
        try {
            readRepository.observeSnapshot(occurrenceId).collect { result ->
                emit(mapReadResult(result))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: CycleCorruptionException) {
            Log.e(TAG, "Answer read corruption", exception)
            emit(AnswerUiState.FatalError(AnswerFatalError.CORRUPTION))
        } catch (exception: Exception) {
            Log.e(TAG, "Answer read failed", exception)
            emit(AnswerUiState.FatalError(AnswerFatalError.LOAD_FAILED))
        }
    }

    private fun mapReadResult(result: AnswerReadResult): AnswerUiState {
        return when (result) {
            AnswerReadResult.Missing ->
                AnswerUiState.Blocked(null, AnswerBlockedReason.NOT_FOUND)
            is AnswerReadResult.Found ->
                mapSnapshot(result.snapshot)
        }
    }

    private fun mapSnapshot(snapshot: AnswerReadSnapshot): AnswerUiState {
        val occurrence = snapshot.occurrence
            ?: return AnswerUiState.Blocked(null, AnswerBlockedReason.NOT_FOUND)

        if (snapshot.hasExistingAnswer && occurrence.status != QuestionOccurrenceStatus.ANSWERED) {
            return AnswerUiState.FatalError(AnswerFatalError.CORRUPTION)
        }

        if (snapshot.isPaused) {
            return blockedForOccurrence(occurrence, AnswerBlockedReason.PRACTICE_PAUSED)
        }

        if (!snapshot.isCurrent) {
            return blockedForStaleOccurrence(occurrence)
        }

        return when (occurrence.status) {
            QuestionOccurrenceStatus.AVAILABLE -> {
                if (snapshot.hasExistingAnswer) {
                    AnswerUiState.FatalError(AnswerFatalError.CORRUPTION)
                } else {
                    interactiveFor(occurrence)
                }
            }
            QuestionOccurrenceStatus.SCHEDULED ->
                AnswerUiState.Blocked(null, AnswerBlockedReason.NOT_AVAILABLE_YET)
            QuestionOccurrenceStatus.ANSWERED,
            QuestionOccurrenceStatus.SKIPPED_BY_USER,
            QuestionOccurrenceStatus.MISSED_BY_TIME,
            -> blockedForOccurrence(occurrence, AnswerBlockedReason.ALREADY_COMPLETED)
        }
    }

    private fun interactiveFor(occurrence: AnswerOccurrenceReadModel): AnswerUiState.Interactive {
        val draft = draftText()
        return AnswerUiState.Interactive(
            questionId = occurrence.questionId,
            questionText = occurrence.questionTextSnapshot,
            draftText = draft,
            isSaving = false,
            canSave = draft.isNotBlank(),
            saveError = null,
        )
    }

    private fun blockedForStaleOccurrence(occurrence: AnswerOccurrenceReadModel): AnswerUiState.Blocked {
        return when (occurrence.status) {
            QuestionOccurrenceStatus.SCHEDULED,
            QuestionOccurrenceStatus.AVAILABLE,
            -> AnswerUiState.Blocked(
                questionText = occurrence.questionTextSnapshot,
                reason = AnswerBlockedReason.NOT_CURRENT,
            )
            else -> blockedForOccurrence(occurrence, AnswerBlockedReason.ALREADY_COMPLETED)
        }
    }

    private fun blockedForOccurrence(
        occurrence: AnswerOccurrenceReadModel,
        reason: AnswerBlockedReason,
    ): AnswerUiState.Blocked {
        val questionText = if (reason == AnswerBlockedReason.NOT_AVAILABLE_YET) {
            null
        } else {
            occurrence.questionTextSnapshot
        }
        return AnswerUiState.Blocked(questionText, reason)
    }

    private companion object {
        const val TAG = "AnswerViewModel"
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
