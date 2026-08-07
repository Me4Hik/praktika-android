// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - модели planner
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

enum class NotificationDeliveryCapability {
    ENABLED,
    DISABLED,
}

enum class BoundaryEventType {
    PLANNED_BOUNDARY,
    EXPIRY_BOUNDARY,
}

data class NotificationOccurrenceSnapshot(
    val occurrenceId: Long,
    val status: QuestionOccurrenceStatus,
    val plannedAtEpochMillis: Long,
    val availableUntilEpochMillis: Long,
    val questionTextSnapshot: String,
    val openedAtEpochMillis: Long?,
)

data class NotificationPlanningInput(
    val isPracticeStarted: Boolean,
    val isPaused: Boolean,
    val nowEpochMillis: Long,
    val currentOccurrence: NotificationOccurrenceSnapshot?,
    val notificationCapability: NotificationDeliveryCapability,
    val activeNotificationOccurrenceId: Long?,
)

data class BoundaryAlarmPlan(
    val occurrenceId: Long,
    val eventType: BoundaryEventType,
    val triggerAtEpochMillis: Long,
    val plannedAtEpochMillis: Long,
)

data class NotificationShowPlan(
    val occurrenceId: Long,
    val plannedAtEpochMillis: Long,
    val questionTextSnapshot: String,
    val soundEnabled: Boolean,
)

data class NotificationPlan(
    val cancelAllAlarms: Boolean = false,
    val plannedBoundaryAlarm: BoundaryAlarmPlan? = null,
    val expiryBoundaryAlarm: BoundaryAlarmPlan? = null,
    val cancelNotification: Boolean = false,
    val showNotification: NotificationShowPlan? = null,
)
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
