// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - planner JVM tests
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPlannerTest {
    private val occurrence = NotificationOccurrenceSnapshot(
        occurrenceId = 10L,
        status = QuestionOccurrenceStatus.SCHEDULED,
        plannedAtEpochMillis = 1_000L,
        availableUntilEpochMillis = 2_000L,
        questionTextSnapshot = "Question text",
        openedAtEpochMillis = null,
    )

    @Test
    fun notStarted_cancelsAlarmsAndNotification() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                isPracticeStarted = false,
                currentOccurrence = occurrence,
            ),
            soundEnabled = true,
        )
        assertTrue(plan.cancelAllAlarms)
        assertTrue(plan.cancelNotification)
        assertNull(plan.showNotification)
    }

    @Test
    fun paused_cancelsAlarmsAndNotification() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                isPaused = true,
                currentOccurrence = occurrence,
            ),
            soundEnabled = true,
        )
        assertTrue(plan.cancelAllAlarms)
        assertTrue(plan.cancelNotification)
    }

    @Test
    fun permissionDisabled_cancelsAlarmsAndNotification() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                notificationCapability = NotificationDeliveryCapability.DISABLED,
                currentOccurrence = occurrence,
            ),
            soundEnabled = true,
        )
        assertTrue(plan.cancelAllAlarms)
        assertTrue(plan.cancelNotification)
    }

    @Test
    fun scheduledBeforePlannedAt_plansBothBoundaries() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 500L,
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.SCHEDULED),
            ),
            soundEnabled = true,
        )
        assertFalse(plan.cancelAllAlarms)
        assertEquals(BoundaryEventType.PLANNED_BOUNDARY, plan.plannedBoundaryAlarm?.eventType)
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        assertEquals(1_000L, plan.plannedBoundaryAlarm?.plannedAtEpochMillis)
        assertEquals(
            1_000L + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS,
            plan.plannedBoundaryAlarm?.triggerAtEpochMillis,
        )
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        assertEquals(BoundaryEventType.EXPIRY_BOUNDARY, plan.expiryBoundaryAlarm?.eventType)
        assertEquals(2_000L, plan.expiryBoundaryAlarm?.triggerAtEpochMillis)
        assertNull(plan.showNotification)
        assertTrue(plan.cancelNotification)
    }

    @Test
    fun scheduledAfterPlannedAt_plansOnlyExpiry() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.SCHEDULED),
            ),
            soundEnabled = true,
        )
        assertNull(plan.plannedBoundaryAlarm)
        assertNotNull(plan.expiryBoundaryAlarm)
    }

    @Test
    fun available_showsNotificationAndPlansExpiry() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.AVAILABLE),
            ),
            soundEnabled = true,
        )
        assertNotNull(plan.showNotification)
        assertEquals(10L, plan.showNotification?.occurrenceId)
        assertNotNull(plan.expiryBoundaryAlarm)
    }

    @Test
    fun activeNotification_doesNotDuplicateShow() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.AVAILABLE),
                activeNotificationOccurrenceId = 10L,
                activeNotificationKind = PracticeNotificationKind.QUESTION,
            ),
            soundEnabled = true,
        )
        assertNull(plan.showNotification)
    }

    @Test
    fun openedAtSet_doesNotShow() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    openedAtEpochMillis = 1_400L,
                ),
            ),
            soundEnabled = true,
        )
        assertNull(plan.showNotification)
    }

    @Test
    fun terminalStatus_cancels() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.ANSWERED),
            ),
            soundEnabled = true,
        )
        assertTrue(plan.cancelAllAlarms)
        assertTrue(plan.cancelNotification)
    }

    @Test
    fun plannedBoundaryNeverBeforeTriggerAt() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 500L,
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.SCHEDULED),
            ),
            soundEnabled = true,
        )
        val trigger = checkNotNull(plan.plannedBoundaryAlarm).triggerAtEpochMillis
        assertTrue(500L < trigger)
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        assertEquals(1_000L + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS, trigger)
        assertEquals(1_000L, plan.plannedBoundaryAlarm?.plannedAtEpochMillis)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    @Test
    fun plannedBiasDoesNotChangeExpiryTrigger() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.SCHEDULED,
                    plannedAtEpochMillis = 10_000L,
                    availableUntilEpochMillis = 20_000L,
                ),
            ),
            soundEnabled = true,
        )
        assertEquals(10_000L, plan.plannedBoundaryAlarm?.plannedAtEpochMillis)
        assertEquals(25_000L, plan.plannedBoundaryAlarm?.triggerAtEpochMillis)
        assertEquals(20_000L, plan.expiryBoundaryAlarm?.triggerAtEpochMillis)
    }

    @Test
    fun availableDeferred_showsSnoozedAndArmsReminder() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    deferredUntilEpochMillis = 1_800L,
                    zoneId = "Europe/Kiev",
                ),
            ),
            soundEnabled = true,
        )
        val shown = checkNotNull(plan.showNotification)
        assertEquals(PracticeNotificationKind.SNOOZED, shown.kind)
        assertEquals(1_800L, shown.deferredUntilEpochMillis)
        assertEquals("Europe/Kiev", shown.zoneId)
        assertFalse(plan.cancelNotification)
        assertNull(plan.plannedBoundaryAlarm)
        assertEquals(BoundaryEventType.EXPIRY_BOUNDARY, plan.expiryBoundaryAlarm?.eventType)
        assertEquals(2_000L, plan.expiryBoundaryAlarm?.triggerAtEpochMillis)
        assertEquals(BoundaryEventType.DEFERRED_REMINDER, plan.deferredReminderAlarm?.eventType)
        assertEquals(1_800L, plan.deferredReminderAlarm?.triggerAtEpochMillis)
    }

    @Test
    fun availableDeferred_repeatedDeferKeepsSnoozedShow() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    deferredUntilEpochMillis = 2_100L,
                ),
                activeNotificationOccurrenceId = 10L,
                activeNotificationKind = PracticeNotificationKind.SNOOZED,
            ),
            soundEnabled = true,
        )
        assertEquals(PracticeNotificationKind.SNOOZED, plan.showNotification?.kind)
        assertEquals(2_100L, plan.showNotification?.deferredUntilEpochMillis)
    }

    @Test
    fun availableDeferred_openedAtStillShowsSnoozedNotification() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    deferredUntilEpochMillis = 1_800L,
                    openedAtEpochMillis = 1_400L,
                ),
            ),
            soundEnabled = true,
        )
        val shown = checkNotNull(plan.showNotification)
        assertEquals(PracticeNotificationKind.SNOOZED, shown.kind)
        assertEquals(1_800L, shown.deferredUntilEpochMillis)
        assertFalse(plan.cancelNotification)
        assertEquals(1_800L, plan.deferredReminderAlarm?.triggerAtEpochMillis)
    }

    @Test
    fun availableDeferred_answeredCancelsNotificationAndAlarms() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.ANSWERED,
                    deferredUntilEpochMillis = 1_800L,
                    openedAtEpochMillis = 1_400L,
                ),
            ),
            soundEnabled = true,
        )
        assertTrue(plan.cancelAllAlarms)
        assertTrue(plan.cancelNotification)
        assertNull(plan.showNotification)
    }

    @Test
    fun availableDeferred_skippedCancelsNotificationAndAlarms() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
                    deferredUntilEpochMillis = 1_800L,
                ),
            ),
            soundEnabled = true,
        )
        assertTrue(plan.cancelAllAlarms)
        assertTrue(plan.cancelNotification)
        assertNull(plan.showNotification)
    }

    @Test
    fun availableDeferredExpired_openedAtDoesNotShowQuestion() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_900L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    deferredUntilEpochMillis = 1_800L,
                    openedAtEpochMillis = 1_400L,
                ),
                activeNotificationOccurrenceId = 10L,
                activeNotificationKind = PracticeNotificationKind.SNOOZED,
            ),
            soundEnabled = true,
        )
        assertNull(plan.showNotification)
        assertFalse(plan.cancelNotification)
        assertNull(plan.deferredReminderAlarm)
    }

    @Test
    fun availableDeferredExpired_forcesQuestionRefreshOverSnoozed() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_900L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    deferredUntilEpochMillis = 1_800L,
                ),
                activeNotificationOccurrenceId = 10L,
                activeNotificationKind = PracticeNotificationKind.SNOOZED,
            ),
            soundEnabled = true,
        )
        assertEquals(PracticeNotificationKind.QUESTION, plan.showNotification?.kind)
        assertFalse(plan.cancelNotification)
        assertNull(plan.deferredReminderAlarm)
        assertEquals(BoundaryEventType.EXPIRY_BOUNDARY, plan.expiryBoundaryAlarm?.eventType)
    }

    @Test
    fun availableQuestionAlreadyPosted_doesNotDuplicateShow() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence.copy(status = QuestionOccurrenceStatus.AVAILABLE),
                activeNotificationOccurrenceId = 10L,
                activeNotificationKind = PracticeNotificationKind.QUESTION,
            ),
            soundEnabled = true,
        )
        assertNull(plan.showNotification)
    }

    @Test
    fun availableDeferredExpired_showsNotificationAgain() {
        val plan = NotificationPlanner.plan(
            input = baseInput(
                nowEpochMillis = 1_900L,
                currentOccurrence = occurrence.copy(
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    deferredUntilEpochMillis = 1_800L,
                ),
            ),
            soundEnabled = true,
        )
        assertNotNull(plan.showNotification)
        assertEquals(PracticeNotificationKind.QUESTION, plan.showNotification?.kind)
        assertFalse(plan.cancelNotification)
        assertNull(plan.deferredReminderAlarm)
        assertEquals(BoundaryEventType.EXPIRY_BOUNDARY, plan.expiryBoundaryAlarm?.eventType)
    }

    @Test
    fun independentSlotsKeepDistinctBiasedTriggers() {
        val slotA = occurrence.copy(
            occurrenceId = 1L,
            plannedAtEpochMillis = 1_000_000L,
            availableUntilEpochMillis = 2_000_000L,
            status = QuestionOccurrenceStatus.SCHEDULED,
        )
        val slotB = occurrence.copy(
            occurrenceId = 2L,
            plannedAtEpochMillis = 3_000_000L,
            availableUntilEpochMillis = 4_000_000L,
            status = QuestionOccurrenceStatus.SCHEDULED,
        )
        val planA = NotificationPlanner.plan(baseInput(nowEpochMillis = 0L, currentOccurrence = slotA), true)
        val planB = NotificationPlanner.plan(baseInput(nowEpochMillis = 0L, currentOccurrence = slotB), true)
        assertEquals(1_015_000L, planA.plannedBoundaryAlarm?.triggerAtEpochMillis)
        assertEquals(3_015_000L, planB.plannedBoundaryAlarm?.triggerAtEpochMillis)
        assertEquals(2_000_000L, planA.expiryBoundaryAlarm?.triggerAtEpochMillis)
        assertEquals(4_000_000L, planB.expiryBoundaryAlarm?.triggerAtEpochMillis)
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private fun baseInput(
        isPracticeStarted: Boolean = true,
        isPaused: Boolean = false,
        nowEpochMillis: Long = 500L,
        currentOccurrence: NotificationOccurrenceSnapshot? = occurrence,
        notificationCapability: NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED,
        activeNotificationOccurrenceId: Long? = null,
        activeNotificationKind: PracticeNotificationKind? = null,
    ): NotificationPlanningInput {
        return NotificationPlanningInput(
            isPracticeStarted = isPracticeStarted,
            isPaused = isPaused,
            nowEpochMillis = nowEpochMillis,
            currentOccurrence = currentOccurrence,
            notificationCapability = notificationCapability,
            activeNotificationOccurrenceId = activeNotificationOccurrenceId,
            activeNotificationKind = activeNotificationKind,
        )
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
