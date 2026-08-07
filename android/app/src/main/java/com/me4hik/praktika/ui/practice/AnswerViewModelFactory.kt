// 05.08.2026 Answer Save cursor by Me4Hik START - factory для AnswerViewModel
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.me4hik.praktika.data.read.RoomAnswerReadRepository
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime

class AnswerViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val runtime: PraktikaRuntime,
    private val occurrenceId: Long,
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        if (modelClass.isAssignableFrom(AnswerViewModel::class.java)) {
            val saveCommand = SaveAnswerCommand { expectedId, text ->
                val result = runtime.cycleRepository.saveAnswer(
                    expectedOccurrenceId = expectedId,
                    answerText = text,
                )
                runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                result
            }
            return AnswerViewModel(
                occurrenceId = occurrenceId,
                readRepository = RoomAnswerReadRepository(runtime.database),
                saveAnswerCommand = saveCommand,
                savedStateHandle = handle,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
