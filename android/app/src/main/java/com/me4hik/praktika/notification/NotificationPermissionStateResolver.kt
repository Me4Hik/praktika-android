// 10.08.2026 Post-release fixes cursor by Me4Hik START - pure permission state machine
package com.me4hik.praktika.notification

internal object NotificationPermissionStateResolver {
    fun resolve(
        supportsRuntimePermission: Boolean,
        runtimeGranted: Boolean,
        permissionEverRequested: Boolean,
        shouldShowRequestPermissionRationale: Boolean,
        appNotificationsEnabled: Boolean,
        selectedChannelBlocked: Boolean,
    ): NotificationPermissionUiState {
        if (supportsRuntimePermission && !runtimeGranted) {
            if (!permissionEverRequested) {
                return NotificationPermissionUiState.NOT_REQUESTED
            }
            if (shouldShowRequestPermissionRationale) {
                return NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED
            }
            return NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED
        }

        if (!appNotificationsEnabled) {
            return NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED
        }

        if (selectedChannelBlocked) {
            return NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED
        }

        return NotificationPermissionUiState.ENABLED
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
