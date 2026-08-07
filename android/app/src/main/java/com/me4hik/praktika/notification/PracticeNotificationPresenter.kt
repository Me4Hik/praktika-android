// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - platform notification boundary
package com.me4hik.praktika.notification

interface PracticeNotificationPresenter {
    fun ensureChannelsCreated()

    fun findActivePracticeNotificationOccurrenceId(): Long?

    fun showNotification(plan: NotificationShowPlan)

    fun cancelPracticeNotification(occurrenceId: Long)

    fun cancelAllPracticeNotifications()
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
