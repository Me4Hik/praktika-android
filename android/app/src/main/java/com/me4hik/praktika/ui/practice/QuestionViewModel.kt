// 05.08.2026 Question And Skip cursor by Me4Hik START - ViewModel экрана вопроса
package com.me4hik.praktika.ui.practice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleDeferNotAllowedException
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.CycleSkipNotAllowedException
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.QuestionOccurrenceReadResult
import com.me4hik.praktika.data.read.QuestionReadRepository
import com.me4hik.praktika.data.read.QuestionReadSnapshot
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
class QuestionViewModel(
    private val occurrenceId: Long,
    private val readRepository: QuestionReadRepository,
    private val skipOccurrenceCommand: SkipOccurrenceCommand,
    private val deferOccurrenceCommand: DeferOccurrenceCommand,
    private val deferDurationMinutesProvider: suspend () -> Int,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val readGeneration = MutableStateFlow(0)
    private val commandState = MutableStateFlow(QuestionCommandState())
    private val navigationEvents = MutableSharedFlow<QuestionNavigationEvent>(extraBufferCapacity = 1)

    private val _uiState = MutableStateFlow<QuestionUiState>(QuestionUiState.Loading)
    val uiState: StateFlow<QuestionUiState> = _uiState.asStateFlow()

    val commandUiState: StateFlow<QuestionCommandState> = commandState.asStateFlow()

    val navigation: SharedFlow<QuestionNavigationEvent> = navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            readGeneration
                .flatMapLatest { observeReadResults() }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun observeReadResults() = flow {
        emit(QuestionUiState.Loading)
        try {
            readRepository.observeOccurrence(occurrenceId).collect { result ->
                emit(mapReadResult(result))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: CycleCorruptionException) {
            Log.e(TAG, "Question read corruption", exception)
            emit(QuestionUiState.FatalError(QuestionFatalError.CORRUPTION))
        } catch (exception: Exception) {
            Log.e(TAG, "Question read failed", exception)
            emit(QuestionUiState.FatalError(QuestionFatalError.LOAD_FAILED))
        }
    }

    fun retryRead() {
        readGeneration.value += 1
    }

    fun onAnswerClicked() {
        val state = _uiState.value
        if (state !is QuestionUiState.Interactive || commandState.value.isBusy) {
            return
        }
        navigationEvents.tryEmit(QuestionNavigationEvent.OpenAnswer(state.question.occurrenceId))
    }

    fun onSkipClicked() {
        if (commandState.value.isBusy) {
            return
        }
        val state = _uiState.value
        if (state !is QuestionUiState.Interactive) {
            return
        }
        commandState.value = commandState.value.copy(isSkipping = true, skipError = null)
        viewModelScope.launch {
            try {
                withContext(commandDispatcher) {
                    skipOccurrenceCommand.skip(occurrenceId)
                }
                navigationEvents.tryEmit(QuestionNavigationEvent.ReturnHomeAfterSkip)
            } catch (_: CycleSkipNotAllowedException) {
                commandState.value = commandState.value.copy(
                    isSkipping = false,
                    skipError = QuestionSkipError.NOT_ALLOWED,
                )
            } catch (exception: CancellationException) {
                commandState.value = commandState.value.copy(isSkipping = false)
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Skip failed", exception)
                commandState.value = commandState.value.copy(
                    isSkipping = false,
                    skipError = QuestionSkipError.FAILED,
                )
            }
        }
    }

    fun onDeferClicked() {
        if (commandState.value.isBusy) {
            return
        }
        val state = _uiState.value
        if (state !is QuestionUiState.Interactive) {
            return
        }
        commandState.value = commandState.value.copy(isDeferring = true, deferError = null)
        viewModelScope.launch {
            try {
                val appliedMinutes = withContext(commandDispatcher) {
                    val durationMinutes = deferDurationMinutesProvider()
                    val result = deferOccurrenceCommand.defer(occurrenceId, durationMinutes)
                    (result as? CycleResult.DeferCompleted)?.durationMinutes ?: durationMinutes
                }
                navigationEvents.tryEmit(
                    QuestionNavigationEvent.ReturnHomeAfterDefer(appliedMinutes),
                )
            } catch (_: CycleDeferNotAllowedException) {
                commandState.value = commandState.value.copy(
                    isDeferring = false,
                    deferError = QuestionDeferError.NOT_ALLOWED,
                )
            } catch (exception: CancellationException) {
                commandState.value = commandState.value.copy(isDeferring = false)
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Defer failed", exception)
                commandState.value = commandState.value.copy(
                    isDeferring = false,
                    deferError = QuestionDeferError.FAILED,
                )
            }
        }
    }

    private fun mapReadResult(result: QuestionOccurrenceReadResult): QuestionUiState {
        return when (result) {
            QuestionOccurrenceReadResult.Missing ->
                QuestionUiState.Blocked(null, QuestionBlockedReason.NOT_FOUND)
            is QuestionOccurrenceReadResult.Found ->
                mapSnapshot(result.snapshot)
        }
    }

    private fun mapSnapshot(snapshot: QuestionReadSnapshot): QuestionUiState {
        val question = snapshot.toUiModel()
        if (snapshot.isPaused) {
            return QuestionUiState.Blocked(question, QuestionBlockedReason.PRACTICE_PAUSED)
        }
        if (!snapshot.isCurrent) {
            return when (snapshot.status) {
                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> QuestionUiState.Blocked(question, QuestionBlockedReason.NOT_CURRENT)
                else -> QuestionUiState.Blocked(question, QuestionBlockedReason.ALREADY_COMPLETED)
            }
        }
        return when (snapshot.status) {
            QuestionOccurrenceStatus.AVAILABLE ->
                QuestionUiState.Interactive(question)
            QuestionOccurrenceStatus.SCHEDULED ->
                QuestionUiState.Blocked(question, QuestionBlockedReason.NOT_AVAILABLE_YET)
            QuestionOccurrenceStatus.ANSWERED,
            QuestionOccurrenceStatus.SKIPPED_BY_USER,
            QuestionOccurrenceStatus.MISSED_BY_TIME,
            -> QuestionUiState.Blocked(question, QuestionBlockedReason.ALREADY_COMPLETED)
        }
    }

    private fun QuestionReadSnapshot.toUiModel(): QuestionUiModel {
        return QuestionUiModel(
            occurrenceId = occurrenceId,
            questionId = questionId,
            questionTextSnapshot = questionTextSnapshot,
            cyclePosition = cyclePosition,
            status = status,
        )
    }

    private companion object {
        const val TAG = "QuestionViewModel"
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
