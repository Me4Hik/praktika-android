// 06.08.2026 Settings Schedule cursor by Me4Hik START - factory для SettingsViewModel
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - sync hooks
// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.ui.settings

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.diagnostics.DiagnosticReportSubmitter
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntime

class SettingsViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val runtime: PraktikaRuntime,
    private val diagnosticReportSubmitter: DiagnosticReportSubmitter,
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                scheduleReadRepository = runtime.scheduleReadRepository,
                practiceReadRepository = RoomPracticeReadRepository(runtime.database),
                updateScheduleCommand = UpdateScheduleCommand { updates ->
                    val result = runtime.cycleRepository.updateSchedule(updates)
                    runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                    result
                },
                pausePracticeCommand = PausePracticeCommand {
                    val result = runtime.cycleRepository.pausePractice()
                    runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                    result
                },
                resumePracticeCommand = ResumePracticeCommand {
                    val result = runtime.cycleRepository.resumePractice()
                    runtime.notificationSyncRequester.requestSync(NotificationSyncReason.MUTATION)
                    result
                },
                soundPreferenceRepository = runtime.soundPreferenceRepository,
                deferDurationPreferenceRepository = runtime.deferDurationPreferenceRepository,
                languagePreferenceRepository = runtime.languagePreferenceRepository,
                questionWordingPreferenceRepository = runtime.questionWordingPreferenceRepository,
                applyQuestionWordingMode = { mode ->
                    runtime.cycleRepository.applyQuestionWordingMode(mode)
                },
                notificationPermissionRepository = runtime.notificationPermissionRepository,
                notificationSyncRequester = runtime.notificationSyncRequester,
                diagnosticReportSubmitter = diagnosticReportSubmitter,
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
                backupSettingsActions = FacadeBackupSettingsActions(runtime.backupSettingsFacade),
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
                savedStateHandle = handle,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 06.08.2026 Settings Schedule cursor by Me4Hik END
