// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission platform boundary
package com.me4hik.praktika.notification

import android.content.Intent
import kotlinx.coroutines.flow.Flow

interface NotificationPermissionPolicy {
    val permissionRequested: Flow<Boolean>

    suspend fun markPermissionRequested()

    fun evaluateUiState(
        permissionRequested: Boolean,
        soundEnabled: Boolean,
    ): NotificationPermissionUiState

    fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability

    fun createAppNotificationSettingsIntent(): Intent

    fun createChannelSettingsIntent(soundEnabled: Boolean): Intent

    fun shouldRequestRuntimePermission(): Boolean

    fun hasRuntimePermission(): Boolean

    fun areAppNotificationsEnabled(): Boolean
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
