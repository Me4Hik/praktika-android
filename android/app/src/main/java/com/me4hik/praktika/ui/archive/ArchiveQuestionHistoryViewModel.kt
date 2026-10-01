// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - ViewModel истории вопроса
// PROMPT 119 — detail from mixed history events (ASC from repository)
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - map units at presentation boundary
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik START - deferCount on history item
package com.me4hik.praktika.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.delete.AnswerDeleteRepository
import com.me4hik.praktika.data.read.ArchiveOccurrenceOutcome
import com.me4hik.praktika.data.read.ArchiveOccurrenceUnit
import com.me4hik.praktika.data.read.ArchiveReadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ArchiveQuestionHistoryViewModel(
    private val archiveReadRepository: ArchiveReadRepository,
    answerDeleteRepository: AnswerDeleteRepository,
    private val zoneIdProvider: ArchiveZoneIdProvider,
    private val displayFormatter: ArchiveDisplayFormatter,
    private val questionId: Int?,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveQuestionHistoryUiState>(
        ArchiveQuestionHistoryUiState.Loading,
    )
    val uiState: StateFlow<ArchiveQuestionHistoryUiState> = _uiState.asStateFlow()

    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - delete actions для question history
    private val deleteController = ArchiveDeleteController(
        answerDeleteRepository = answerDeleteRepository,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
    )
    val deleteUiState: StateFlow<ArchiveDeleteUiState> = deleteController.deleteUiState

    fun requestDelete(answerId: Long) = deleteController.requestDelete(answerId)

    fun cancelDelete() = deleteController.cancelDelete()

    fun confirmDelete() = deleteController.confirmDelete()

    fun clearDeleteError() = deleteController.clearDeleteError()
    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END

    init {
        if (questionId == null || questionId <= 0) {
            _uiState.value = ArchiveQuestionHistoryUiState.Error(message = "Invalid archive question route")
        } else {
            observeQuestionHistory(questionId)
        }
    }

    private fun observeQuestionHistory(questionId: Int) {
        viewModelScope.launch {
            val zoneId = zoneIdProvider.currentZoneId()
            archiveReadRepository.observeOccurrenceHistoryForQuestion(questionId)
                .catch { throwable ->
                    _uiState.value = ArchiveQuestionHistoryUiState.Error(
                        message = throwable.message ?: "Archive question read failed",
                    )
                }
                .collect { units ->
                    _uiState.value = if (units.isEmpty()) {
                        ArchiveQuestionHistoryUiState.Empty
                    } else {
                        ArchiveQuestionHistoryUiState.Content(
                            entries = units.map { unit ->
                                unit.toHistoryItem(displayFormatter, zoneId)
                            },
                        )
                    }
                }
        }
    }

    private companion object {
        fun ArchiveOccurrenceUnit.toHistoryItem(
            displayFormatter: ArchiveDisplayFormatter,
            zoneId: java.time.ZoneId,
        ): ArchiveQuestionHistoryItem {
            val dateTimeText = displayFormatter.formatDateTimeLine(eventAtEpochMillis, zoneId)
            val mappedDeferCount = deferCount
            return when (outcome) {
                ArchiveOccurrenceOutcome.ANSWERED -> {
                    val liveAnswer = answerId != null
                    ArchiveQuestionHistoryItem(
                        stableKey = stableKey,
                        kind = ArchiveHistoryItemKind.Answer,
                        occurrenceId = occurrenceId,
                        questionText = questionTextSnapshot,
                        answerId = answerId,
                        answerText = answerText,
                        dateTimeText = dateTimeText,
                        cycleNumber = cycleNumber,
                        cyclePosition = cyclePosition,
                        deferCount = mappedDeferCount,
                        canShare = liveAnswer,
                        canDelete = liveAnswer,
                    )
                }
                ArchiveOccurrenceOutcome.REJECTED -> ArchiveQuestionHistoryItem(
                    stableKey = stableKey,
                    kind = ArchiveHistoryItemKind.Rejected,
                    occurrenceId = occurrenceId,
                    questionText = questionTextSnapshot,
                    answerId = null,
                    answerText = null,
                    dateTimeText = dateTimeText,
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    deferCount = mappedDeferCount,
                    canShare = false,
                    canDelete = false,
                )
                ArchiveOccurrenceOutcome.MISSED -> ArchiveQuestionHistoryItem(
                    stableKey = stableKey,
                    kind = ArchiveHistoryItemKind.Missed,
                    occurrenceId = occurrenceId,
                    questionText = questionTextSnapshot,
                    answerId = null,
                    answerText = null,
                    dateTimeText = dateTimeText,
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    deferCount = mappedDeferCount,
                    canShare = false,
                    canDelete = false,
                )
            }
        }
    }
}
// 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik END
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
