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
    DEFERRED_REMINDER,
}

enum class PracticeNotificationKind {
    QUESTION,
    SNOOZED,
}

data class NotificationOccurrenceSnapshot(
    val occurrenceId: Long,
    val status: QuestionOccurrenceStatus,
    val plannedAtEpochMillis: Long,
    val availableUntilEpochMillis: Long,
    val questionTextSnapshot: String,
    val openedAtEpochMillis: Long?,
    val deferredUntilEpochMillis: Long? = null,
    val zoneId: String = "UTC",
)

data class NotificationPlanningInput(
    val isPracticeStarted: Boolean,
    val isPaused: Boolean,
    val nowEpochMillis: Long,
    val currentOccurrence: NotificationOccurrenceSnapshot?,
    val notificationCapability: NotificationDeliveryCapability,
    val activeNotificationOccurrenceId: Long?,
    val activeNotificationKind: PracticeNotificationKind? = null,
    /** When true, due QUESTION re-posts without audible/heads-up alert (FOREGROUND catch-up). */
    val quietCatchUp: Boolean = false,
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
    val kind: PracticeNotificationKind = PracticeNotificationKind.QUESTION,
    val deferredUntilEpochMillis: Long? = null,
    val zoneId: String = "UTC",
    /** Quiet re-materialize: same content/actions, no sound / heads-up interruption. */
    val suppressAlert: Boolean = false,
    /** Stable sound-library id; ignored when [soundEnabled] is false or [suppressAlert] is true. */
    val selectedSoundId: String = com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT,
)

data class NotificationPlan(
    val cancelAllAlarms: Boolean = false,
    val plannedBoundaryAlarm: BoundaryAlarmPlan? = null,
    val expiryBoundaryAlarm: BoundaryAlarmPlan? = null,
    val deferredReminderAlarm: BoundaryAlarmPlan? = null,
    val cancelNotification: Boolean = false,
    val showNotification: NotificationShowPlan? = null,
)
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
