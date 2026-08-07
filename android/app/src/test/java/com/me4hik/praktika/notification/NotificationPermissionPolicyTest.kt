// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission policy JVM tests
package com.me4hik.praktika.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun api33NotRequested_isNotRequestedState() {
        val state = evaluate(
            appEnabled = false,
            permissionRequested = false,
            channelBlocked = false,
            soundEnabled = true,
        )
        assertEquals(NotificationPermissionUiState.NOT_REQUESTED, state)
    }

    @Test
    fun deniedAfterRequest_isDeniedState() {
        val state = evaluate(
            appEnabled = false,
            permissionRequested = true,
            channelBlocked = false,
            soundEnabled = true,
        )
        assertEquals(NotificationPermissionUiState.DENIED_OR_DISABLED, state)
    }

    @Test
    fun grantedAndChannelEnabled_isEnabled() {
        val state = evaluate(
            appEnabled = true,
            permissionRequested = true,
            channelBlocked = false,
            soundEnabled = true,
        )
        assertEquals(NotificationPermissionUiState.ENABLED, state)
    }

    @Test
    fun selectedChannelBlocked_isChannelDisabled() {
        val state = evaluate(
            appEnabled = true,
            permissionRequested = true,
            channelBlocked = true,
            soundEnabled = true,
        )
        assertEquals(NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED, state)
    }

    @Test
    fun apiBelow33Disabled_isDeniedNotNotRequested() {
        val state = evaluate(
            appEnabled = false,
            permissionRequested = false,
            channelBlocked = false,
            soundEnabled = true,
            treatNotRequestedAsDeniedWhenDisabled = true,
        )
        assertEquals(NotificationPermissionUiState.DENIED_OR_DISABLED, state)
    }

    private fun evaluate(
        appEnabled: Boolean,
        permissionRequested: Boolean,
        channelBlocked: Boolean,
        soundEnabled: Boolean,
        treatNotRequestedAsDeniedWhenDisabled: Boolean = false,
    ): NotificationPermissionUiState {
        if (!appEnabled) {
            return if (!permissionRequested && !treatNotRequestedAsDeniedWhenDisabled) {
                NotificationPermissionUiState.NOT_REQUESTED
            } else {
                NotificationPermissionUiState.DENIED_OR_DISABLED
            }
        }
        if (channelBlocked) {
            return NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED
        }
        return NotificationPermissionUiState.ENABLED
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
