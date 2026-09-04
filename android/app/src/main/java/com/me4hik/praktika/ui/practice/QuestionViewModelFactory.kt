// 05.08.2026 Question And Skip cursor by Me4Hik START - factory для QuestionViewModel
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.read.RoomQuestionReadRepository
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime
import kotlinx.coroutines.flow.first

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
            val deferCommand = DeferOccurrenceCommand { expectedId, durationMinutes ->
                val result = runtime.cycleRepository.deferAvailableOccurrence(
                    expectedOccurrenceId = expectedId,
                    durationMinutes = durationMinutes,
                )
                runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                result
            }
            return QuestionViewModel(
                occurrenceId = occurrenceId,
                readRepository = RoomQuestionReadRepository(runtime.database),
                skipOccurrenceCommand = skipCommand,
                deferOccurrenceCommand = deferCommand,
                deferDurationMinutesProvider = {
                    DeferDurationOptions.sanitize(
                        runtime.deferDurationPreferenceRepository.deferDurationMinutes.first(),
                    )
                },
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
