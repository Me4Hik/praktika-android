// 05.08.2026 Main Screen cursor by Me4Hik START - factory для PracticeRootViewModel
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - saved-state-aware factory
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - notification wiring и sync hooks
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.ui.settings.UpdateScheduleCommand

class PracticeRootViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val runtime: PraktikaRuntime,
    private val onRequestPostNotifications: () -> Unit,
    private val onOpenAppNotificationSettings: () -> Unit,
    private val onOpenChannelSettings: () -> Unit,
    private val onOpenExactAlarmSettings: () -> Unit = {},
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        if (modelClass.isAssignableFrom(PracticeRootViewModel::class.java)) {
            return PracticeRootViewModel(
                readRepository = RoomPracticeReadRepository(runtime.database),
                scheduleReadRepository = runtime.scheduleReadRepository,
                startPracticeCommand = StartPracticeCommand {
                    val result = runtime.cycleRepository.startPractice()
                    runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                    result
                },
                updateScheduleCommand = UpdateScheduleCommand { updates ->
                    val result = runtime.cycleRepository.updateSchedule(updates)
                    runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                    result
                },
                notificationPermissionRepository = runtime.notificationPermissionRepository,
                exactAlarmCapabilityRepository = runtime.exactAlarmCapabilityRepository,
                soundPreferenceRepository = runtime.soundPreferenceRepository,
                onRequestPostNotifications = onRequestPostNotifications,
                onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                onOpenChannelSettings = onOpenChannelSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                savedStateHandle = handle,
                timeFormatter = PracticeTimeFormatter(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
