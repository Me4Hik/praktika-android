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
    /**
     * When true (SOUND_CHANGED), allow re-showing the current due QUESTION even if shade
     * already has QUESTION for the same occurrence — so routing/channel can be refreshed.
     */
    val forceRefreshDueQuestion: Boolean = false,
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
    /**
     * FOREGROUND quiet catch-up: no interruption and route via [PracticeNotificationChannels.DUE_SILENT].
     * Must not be used for SOUND_CHANGED channel refresh.
     */
    val suppressAlert: Boolean = false,
    /**
     * SOUND_CHANGED quiet refresh: no aggressive alert, but keep normal sound routing
     * (custom / due_sound / due_silent from prefs) — do not force DUE_SILENT.
     */
    val quietUpdateKeepRouting: Boolean = false,
    /** Stable sound-library id; ignored when [soundEnabled] is false. */
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
