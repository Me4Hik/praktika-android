// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - test notification doubles
package com.me4hik.praktika.runtime

import android.content.Intent
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationPlan
import com.me4hik.praktika.notification.NotificationShowPlan
import com.me4hik.praktika.notification.PlatformAlarmScheduler
import com.me4hik.praktika.notification.PracticeNotificationPresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

class NoOpPlatformAlarmScheduler : PlatformAlarmScheduler {
    override fun scheduleAlarms(plan: NotificationPlan, previousAlarms: List<BoundaryAlarmPlan>) = Unit

    override fun cancelAlarms(alarms: List<BoundaryAlarmPlan>) = Unit
}

class NoOpPracticeNotificationPresenter : PracticeNotificationPresenter {
    override fun ensureChannelsCreated() = Unit

    override fun findActivePracticeNotificationOccurrenceId(): Long? = null

    override fun showNotification(plan: NotificationShowPlan) = Unit

    override fun cancelPracticeNotification(occurrenceId: Long) = Unit

    override fun cancelAllPracticeNotifications() = Unit
}

// 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik START - granted permission test double
class GrantedNotificationPermissionPolicy : NotificationPermissionPolicy {
    private val _permissionStateRevision = MutableStateFlow(0L)
    override val permissionStateRevision: StateFlow<Long> = _permissionStateRevision.asStateFlow()

    override fun notifyPermissionStateChanged(source: String) {
        _permissionStateRevision.value = _permissionStateRevision.value + 1L
    }

    override val permissionRequested: Flow<Boolean> = flowOf(true)

    override suspend fun markPermissionRequested() = Unit

    override fun evaluateUiState(
        permissionRequested: Boolean,
        soundEnabled: Boolean,
    ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED

    override fun toDeliveryCapability(
        state: NotificationPermissionUiState,
    ): NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED

    override fun createAppNotificationSettingsIntent(): Intent = Intent()

    override fun createChannelSettingsIntent(soundEnabled: Boolean): Intent = Intent()

    override fun shouldRequestRuntimePermission(): Boolean = true

    override fun hasRuntimePermission(): Boolean = true

    override fun areAppNotificationsEnabled(): Boolean = true

    override fun shouldShowRequestPermissionRationale(): Boolean = false

    override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean = true
}
// 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
