// 05.08.2026 Question And Skip cursor by Me4Hik START - factory для QuestionViewModel
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.me4hik.praktika.data.read.RoomQuestionReadRepository
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime

class QuestionViewModelFactory(
    private val runtime: PraktikaRuntime,
    private val occurrenceId: Long,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuestionViewModel::class.java)) {
            val skipCommand = SkipOccurrenceCommand { expectedId ->
                val result = runtime.cycleRepository.skipAvailableByUser(expectedId)
                runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                result
            }
            return QuestionViewModel(
                occurrenceId = occurrenceId,
                readRepository = RoomQuestionReadRepository(runtime.database),
                skipOccurrenceCommand = skipCommand,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
