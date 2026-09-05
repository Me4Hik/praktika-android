// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission platform boundary
// 10.08.2026 Post-release fixes cursor by Me4Hik START - rationale + channel probe
package com.me4hik.praktika.notification

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NotificationPermissionPolicy {
    val permissionRequested: Flow<Boolean>

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - permission UI refresh trigger
    val permissionStateRevision: StateFlow<Long>

    fun notifyPermissionStateChanged(source: String)
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    suspend fun markPermissionRequested()

    fun evaluateUiState(
        permissionRequested: Boolean,
        soundEnabled: Boolean,
        selectedSoundId: String = com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT,
    ): NotificationPermissionUiState

    fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability

    fun createAppNotificationSettingsIntent(): Intent

    fun createChannelSettingsIntent(
        soundEnabled: Boolean,
        selectedSoundId: String = com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT,
    ): Intent

    fun shouldRequestRuntimePermission(): Boolean

    fun hasRuntimePermission(): Boolean

    fun areAppNotificationsEnabled(): Boolean

    fun shouldShowRequestPermissionRationale(): Boolean

    fun isSelectedChannelEnabled(
        soundEnabled: Boolean,
        selectedSoundId: String = com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT,
    ): Boolean
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
