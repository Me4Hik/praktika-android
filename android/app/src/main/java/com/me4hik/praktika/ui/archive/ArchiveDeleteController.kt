// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - transient delete state для archive ViewModels
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.delete.AnswerDeleteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveDeleteController(
    private val answerDeleteRepository: AnswerDeleteRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _deleteUiState = MutableStateFlow(ArchiveDeleteUiState())
    val deleteUiState: StateFlow<ArchiveDeleteUiState> = _deleteUiState.asStateFlow()

    fun requestDelete(answerId: Long) {
        _deleteUiState.update {
            it.copy(
                pendingDeleteAnswerId = answerId,
                deleteError = null,
            )
        }
    }

    fun cancelDelete() {
        if (_deleteUiState.value.isDeleting) {
            return
        }
        _deleteUiState.value = ArchiveDeleteUiState()
    }

    fun confirmDelete() {
        val answerId = _deleteUiState.value.pendingDeleteAnswerId ?: return
        if (_deleteUiState.value.isDeleting) {
            return
        }
        _deleteUiState.update { it.copy(isDeleting = true, deleteError = null) }
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    answerDeleteRepository.deleteAnswer(answerId)
                }
                _deleteUiState.value = ArchiveDeleteUiState()
            } catch (_: Exception) {
                _deleteUiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = DELETE_FAILED,
                    )
                }
            }
        }
    }

    fun clearDeleteError() {
        _deleteUiState.update { it.copy(deleteError = null) }
    }

    companion object {
        const val DELETE_FAILED = "delete_failed"
    }
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
