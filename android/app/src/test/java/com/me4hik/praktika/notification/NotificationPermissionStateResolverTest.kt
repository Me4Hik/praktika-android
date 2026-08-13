// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission policy JVM tests
// 10.08.2026 Post-release fixes cursor by Me4Hik START - split recovery state matrix
package com.me4hik.praktika.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionStateResolverTest {
    @Test
    fun firstInstall_offersNotRequested() {
        assertEquals(
            NotificationPermissionUiState.NOT_REQUESTED,
            resolve(
                runtimeGranted = false,
                permissionEverRequested = false,
            ),
        )
    }

    @Test
    fun firstDenyWithRationale_offersRuntimeRetry() {
        assertEquals(
            NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED,
            resolve(
                runtimeGranted = false,
                permissionEverRequested = true,
                shouldShowRequestPermissionRationale = true,
            ),
        )
    }

    @Test
    fun permanentRuntimeDeny_opensAppSettingsPath() {
        assertEquals(
            NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED,
            resolve(
                runtimeGranted = false,
                permissionEverRequested = true,
                shouldShowRequestPermissionRationale = false,
            ),
        )
    }

    @Test
    fun existingVc3LegacyState_notStuckOnOldFlag() {
        assertEquals(
            NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED,
            resolve(
                runtimeGranted = false,
                permissionEverRequested = true,
                shouldShowRequestPermissionRationale = false,
                appNotificationsEnabled = false,
            ),
        )
    }

    @Test
    fun existingVc3LegacyState_withRationale_canRetryRuntime() {
        assertEquals(
            NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED,
            resolve(
                runtimeGranted = false,
                permissionEverRequested = true,
                shouldShowRequestPermissionRationale = true,
                appNotificationsEnabled = false,
            ),
        )
    }

    @Test
    fun grantWithAppAndChannelEnabled_isEnabled() {
        assertEquals(
            NotificationPermissionUiState.ENABLED,
            resolve(
                runtimeGranted = true,
                permissionEverRequested = true,
                appNotificationsEnabled = true,
                selectedChannelBlocked = false,
            ),
        )
    }

    @Test
    fun appMasterDisabled_opensAppSettings() {
        assertEquals(
            NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED,
            resolve(
                runtimeGranted = true,
                permissionEverRequested = true,
                appNotificationsEnabled = false,
            ),
        )
    }

    @Test
    fun channelOnlyDisabled_opensChannelSettings() {
        assertEquals(
            NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED,
            resolve(
                runtimeGranted = true,
                permissionEverRequested = true,
                appNotificationsEnabled = true,
                selectedChannelBlocked = true,
            ),
        )
    }

    @Test
    fun apiBelow33Disabled_usesAppSettings() {
        assertEquals(
            NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED,
            NotificationPermissionStateResolver.resolve(
                supportsRuntimePermission = false,
                runtimeGranted = false,
                permissionEverRequested = false,
                shouldShowRequestPermissionRationale = false,
                appNotificationsEnabled = false,
                selectedChannelBlocked = false,
            ),
        )
    }

    private fun resolve(
        runtimeGranted: Boolean,
        permissionEverRequested: Boolean,
        shouldShowRequestPermissionRationale: Boolean = false,
        appNotificationsEnabled: Boolean = false,
        selectedChannelBlocked: Boolean = false,
    ): NotificationPermissionUiState {
        return NotificationPermissionStateResolver.resolve(
            supportsRuntimePermission = true,
            runtimeGranted = runtimeGranted,
            permissionEverRequested = permissionEverRequested,
            shouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale,
            appNotificationsEnabled = appNotificationsEnabled,
            selectedChannelBlocked = selectedChannelBlocked,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
