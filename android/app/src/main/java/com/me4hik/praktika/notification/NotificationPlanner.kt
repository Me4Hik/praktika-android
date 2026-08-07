// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - pure planner policy
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

object NotificationPlanner {
    fun plan(input: NotificationPlanningInput, soundEnabled: Boolean): NotificationPlan {
        if (!input.isPracticeStarted || input.isPaused) {
            return NotificationPlan(cancelAllAlarms = true, cancelNotification = true)
        }
        if (input.notificationCapability != NotificationDeliveryCapability.ENABLED) {
            return NotificationPlan(cancelAllAlarms = true, cancelNotification = true)
        }

        val occurrence = input.currentOccurrence
            ?: return NotificationPlan(cancelAllAlarms = true, cancelNotification = true)

        return when (occurrence.status) {
            QuestionOccurrenceStatus.SCHEDULED -> planScheduled(input.nowEpochMillis, occurrence)
            QuestionOccurrenceStatus.AVAILABLE -> planAvailable(input, occurrence, soundEnabled)
            else -> NotificationPlan(cancelAllAlarms = true, cancelNotification = true)
        }
    }

    private fun planScheduled(
        nowEpochMillis: Long,
        occurrence: NotificationOccurrenceSnapshot,
    ): NotificationPlan {
        val plannedAlarm = if (nowEpochMillis < occurrence.plannedAtEpochMillis) {
            boundaryAlarm(occurrence, BoundaryEventType.PLANNED_BOUNDARY, occurrence.plannedAtEpochMillis)
        } else {
            null
        }
        val expiryAlarm = boundaryAlarm(
            occurrence,
            BoundaryEventType.EXPIRY_BOUNDARY,
            occurrence.availableUntilEpochMillis,
        )
        return NotificationPlan(
            cancelAllAlarms = false,
            plannedBoundaryAlarm = plannedAlarm,
            expiryBoundaryAlarm = expiryAlarm,
            cancelNotification = true,
            showNotification = null,
        )
    }

    private fun planAvailable(
        input: NotificationPlanningInput,
        occurrence: NotificationOccurrenceSnapshot,
        soundEnabled: Boolean,
    ): NotificationPlan {
        val shouldShow = occurrence.openedAtEpochMillis == null &&
            input.activeNotificationOccurrenceId != occurrence.occurrenceId
        return NotificationPlan(
            cancelAllAlarms = false,
            plannedBoundaryAlarm = null,
            expiryBoundaryAlarm = boundaryAlarm(
                occurrence,
                BoundaryEventType.EXPIRY_BOUNDARY,
                occurrence.availableUntilEpochMillis,
            ),
            cancelNotification = false,
            showNotification = if (shouldShow) {
                NotificationShowPlan(
                    occurrenceId = occurrence.occurrenceId,
                    plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
                    questionTextSnapshot = occurrence.questionTextSnapshot,
                    soundEnabled = soundEnabled,
                )
            } else {
                null
            },
        )
    }

    private fun boundaryAlarm(
        occurrence: NotificationOccurrenceSnapshot,
        eventType: BoundaryEventType,
        triggerAtEpochMillis: Long,
    ): BoundaryAlarmPlan {
        return BoundaryAlarmPlan(
            occurrenceId = occurrence.occurrenceId,
            eventType = eventType,
            triggerAtEpochMillis = triggerAtEpochMillis,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
        )
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
