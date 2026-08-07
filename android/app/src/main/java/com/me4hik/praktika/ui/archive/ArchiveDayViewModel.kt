// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - ViewModel выбранного дня
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
import java.time.LocalDate

class ArchiveDayViewModel(
    private val archiveReadRepository: ArchiveReadRepository,
    answerDeleteRepository: AnswerDeleteRepository,
    private val zoneIdProvider: ArchiveZoneIdProvider,
    private val displayFormatter: ArchiveDisplayFormatter,
    private val epochDay: Long?,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveDayUiState>(ArchiveDayUiState.Loading)
    val uiState: StateFlow<ArchiveDayUiState> = _uiState.asStateFlow()

    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - delete actions для day archive
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
        if (epochDay == null) {
            _uiState.value = ArchiveDayUiState.Error(message = "Invalid archive day route")
        } else {
            observeDay(epochDay)
        }
    }

    private fun observeDay(epochDay: Long) {
        viewModelScope.launch {
            val zoneId = zoneIdProvider.currentZoneId()
            val date = runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull()
            if (date == null) {
                _uiState.value = ArchiveDayUiState.Error(message = "Invalid archive day route")
                return@launch
            }
            val bounds = ArchiveDayRange.bounds(epochDay, zoneId)
            val titleDateText = displayFormatter.formatDate(
                date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                zoneId,
            )
            archiveReadRepository.observeEntriesInRange(
                startInclusiveEpochMillis = bounds.startInclusiveEpochMillis,
                endExclusiveEpochMillis = bounds.endExclusiveEpochMillis,
            )
                .catch { throwable ->
                    _uiState.value = ArchiveDayUiState.Error(
                        message = throwable.message ?: "Archive day read failed",
                    )
                }
                .collect { entries ->
                    _uiState.value = if (entries.isEmpty()) {
                        ArchiveDayUiState.Empty
                    } else {
                        ArchiveDayUiState.Content(
                            titleDateText = titleDateText,
                            entries = entries.map { entry ->
                                ArchiveDayEntryUi(
                                    answerId = entry.answerId,
                                    questionText = entry.questionText,
                                    answerText = entry.answerText,
                                    dateText = displayFormatter.formatDate(
                                        entry.answeredAtEpochMillis,
                                        zoneId,
                                    ),
                                    timeText = displayFormatter.formatTime(
                                        entry.answeredAtEpochMillis,
                                        zoneId,
                                    ),
                                )
                            },
                        )
                    }
                }
        }
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
