// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - ViewModel списка вопросов архива
// PROMPT 119 — list from mixed history events
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - list from occurrence units
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik - list without deferredCount
package com.me4hik.praktika.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.read.ArchiveReadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ArchiveQuestionsViewModel(
    private val archiveReadRepository: ArchiveReadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveQuestionsUiState>(ArchiveQuestionsUiState.Loading)
    val uiState: StateFlow<ArchiveQuestionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            archiveReadRepository.observeAllOccurrenceHistory()
                .catch { throwable ->
                    _uiState.value = ArchiveQuestionsUiState.Error(
                        message = throwable.message ?: "Archive read failed",
                    )
                }
                .collect { units ->
                    val summarized = ArchiveHistoryQuestionGrouping.summarize(units)
                    _uiState.value = if (summarized.isEmpty()) {
                        ArchiveQuestionsUiState.Empty
                    } else {
                        ArchiveQuestionsUiState.Content(
                            questions = summarized.map { summary ->
                                ArchiveQuestionListItem(
                                    questionId = summary.questionId,
                                    questionText = summary.questionText,
                                    cyclePosition = summary.cyclePosition,
                                    latestEventAtEpochMillis = summary.latestEventAtEpochMillis,
                                    answerCount = summary.answerCount,
                                    rejectedCount = summary.rejectedCount,
                                    missedCount = summary.missedCount,
                                )
                            },
                        )
                    }
                }
        }
    }
}
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
