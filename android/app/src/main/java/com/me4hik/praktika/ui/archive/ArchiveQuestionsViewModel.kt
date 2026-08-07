// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - ViewModel списка вопросов архива
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
            archiveReadRepository.observeEntries()
                .catch { throwable ->
                    _uiState.value = ArchiveQuestionsUiState.Error(
                        message = throwable.message ?: "Archive read failed",
                    )
                }
                .collect { entries ->
                    val grouped = ArchiveQuestionGrouping.groupQuestions(entries)
                    _uiState.value = if (grouped.isEmpty()) {
                        ArchiveQuestionsUiState.Empty
                    } else {
                        ArchiveQuestionsUiState.Content(
                            questions = grouped.map { group ->
                                ArchiveQuestionListItem(
                                    questionId = group.questionId,
                                    questionText = group.questionText,
                                    answerCount = group.answerCount,
                                )
                            },
                        )
                    }
                }
        }
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
