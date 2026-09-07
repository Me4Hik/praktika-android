// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - pure planner policy
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

object NotificationPlanner {
    fun plan(
        input: NotificationPlanningInput,
        soundEnabled: Boolean,
        selectedSoundId: String = com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT,
    ): NotificationPlan {
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
            QuestionOccurrenceStatus.AVAILABLE ->
                planAvailable(input, occurrence, soundEnabled, selectedSoundId)
            else -> NotificationPlan(cancelAllAlarms = true, cancelNotification = true)
        }
    }

    private fun planScheduled(
        nowEpochMillis: Long,
        occurrence: NotificationOccurrenceSnapshot,
    ): NotificationPlan {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        val plannedAlarm = if (nowEpochMillis < occurrence.plannedAtEpochMillis) {
            boundaryAlarm(
                occurrence = occurrence,
                eventType = BoundaryEventType.PLANNED_BOUNDARY,
                triggerAtEpochMillis = PlannedBoundaryTiming.biasedTriggerAt(occurrence.plannedAtEpochMillis),
            )
        } else {
            null
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
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
        selectedSoundId: String,
    ): NotificationPlan {
        val deferredUntil = occurrence.deferredUntilEpochMillis
        val isDeferred = deferredUntil != null && input.nowEpochMillis < deferredUntil
        if (isDeferred) {
            // Clock-like snooze stays in the shade even if the user opened the question.
            return NotificationPlan(
                cancelAllAlarms = false,
                plannedBoundaryAlarm = null,
                expiryBoundaryAlarm = boundaryAlarm(
                    occurrence,
                    BoundaryEventType.EXPIRY_BOUNDARY,
                    occurrence.availableUntilEpochMillis,
                ),
                deferredReminderAlarm = boundaryAlarm(
                    occurrence,
                    BoundaryEventType.DEFERRED_REMINDER,
                    deferredUntil!!,
                ),
                cancelNotification = false,
                showNotification = NotificationShowPlan(
                    occurrenceId = occurrence.occurrenceId,
                    plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
                    questionTextSnapshot = occurrence.questionTextSnapshot,
                    soundEnabled = soundEnabled,
                    kind = PracticeNotificationKind.SNOOZED,
                    deferredUntilEpochMillis = deferredUntil,
                    zoneId = occurrence.zoneId,
                    selectedSoundId = selectedSoundId,
                ),
            )
        }

        val shouldShow = occurrence.openedAtEpochMillis == null &&
            shouldShowDueQuestion(input, occurrence.occurrenceId)
        return NotificationPlan(
            cancelAllAlarms = false,
            plannedBoundaryAlarm = null,
            expiryBoundaryAlarm = boundaryAlarm(
                occurrence,
                BoundaryEventType.EXPIRY_BOUNDARY,
                occurrence.availableUntilEpochMillis,
            ),
            deferredReminderAlarm = null,
            cancelNotification = false,
            showNotification = if (shouldShow) {
                NotificationShowPlan(
                    occurrenceId = occurrence.occurrenceId,
                    plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
                    questionTextSnapshot = occurrence.questionTextSnapshot,
                    soundEnabled = soundEnabled,
                    kind = PracticeNotificationKind.QUESTION,
                    zoneId = occurrence.zoneId,
                    suppressAlert = input.quietCatchUp,
                    quietUpdateKeepRouting = input.forceRefreshDueQuestion && !input.quietCatchUp,
                    selectedSoundId = selectedSoundId,
                )
            } else {
                null
            },
        )
    }

    /**
     * Show when shade empty / different occurrence / SNOOZED maturity.
     * Normally do not re-post if QUESTION for this id is already active —
     * except [NotificationPlanningInput.forceRefreshDueQuestion] (SOUND_CHANGED).
     */
    private fun shouldShowDueQuestion(
        input: NotificationPlanningInput,
        occurrenceId: Long,
    ): Boolean {
        if (input.forceRefreshDueQuestion) {
            return true
        }
        val activeId = input.activeNotificationOccurrenceId
        if (activeId == null) {
            return true
        }
        if (activeId != occurrenceId) {
            return true
        }
        return input.activeNotificationKind != PracticeNotificationKind.QUESTION
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
