// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import com.me4hik.praktika.notification.NotificationPermissionPolicy
import kotlinx.coroutines.flow.first

object DiagnosticPermissionRecorder {
    suspend fun recordState(
        permissionRepository: NotificationPermissionPolicy,
        soundEnabled: Boolean,
        source: String,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        val requested = permissionRepository.permissionRequested.first()
        val uiState = permissionRepository.evaluateUiState(
            permissionRequested = requested,
            soundEnabled = soundEnabled,
        )
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.PERMISSION,
            name = "notification_permission_state",
            metadata = mapOf(
                "source" to source,
                "resolved_ui_state" to uiState.name,
                "requested_flag" to requested.toString(),
                "runtime_granted" to permissionRepository.hasRuntimePermission().toString(),
                "should_show_rationale" to permissionRepository.shouldShowRequestPermissionRationale().toString(),
                "app_notifications_enabled" to permissionRepository.areAppNotificationsEnabled().toString(),
                "selected_channel_enabled" to permissionRepository.isSelectedChannelEnabled(soundEnabled).toString(),
            ),
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
