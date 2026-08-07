// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - ViewModel истории вопроса
package com.me4hik.praktika.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.delete.AnswerDeleteRepository
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
            archiveReadRepository.observeEntriesForQuestion(questionId)
                .catch { throwable ->
                    _uiState.value = ArchiveQuestionHistoryUiState.Error(
                        message = throwable.message ?: "Archive question read failed",
                    )
                }
                .collect { entries ->
                    _uiState.value = if (entries.isEmpty()) {
                        ArchiveQuestionHistoryUiState.Empty
                    } else {
                        ArchiveQuestionHistoryUiState.Content(
                            entries = entries.map { entry ->
                                ArchiveQuestionHistoryItem(
                                    answerId = entry.answerId,
                                    questionText = entry.questionText,
                                    answerText = entry.answerText,
                                    dateTimeText = displayFormatter.formatDateTimeLine(
                                        entry.answeredAtEpochMillis,
                                        zoneId,
                                    ),
                                    cycleNumber = entry.cycleNumber,
                                )
                            },
                        )
                    }
                }
        }
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
