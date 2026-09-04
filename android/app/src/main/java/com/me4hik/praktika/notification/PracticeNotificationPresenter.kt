// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - platform notification boundary
package com.me4hik.praktika.notification

interface PracticeNotificationPresenter {
    fun ensureChannelsCreated()

    fun findActivePracticeNotificationOccurrenceId(): Long?

    fun findActivePracticeNotificationKind(): PracticeNotificationKind?

    fun showNotification(plan: NotificationShowPlan)

    fun cancelCurrentPracticeNotification()

    fun cancelLegacyPracticeNotifications(
        currentOccurrenceId: Long? = null,
        syncReason: String? = null,
    )

    fun cancelAllPracticeNotifications()
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
