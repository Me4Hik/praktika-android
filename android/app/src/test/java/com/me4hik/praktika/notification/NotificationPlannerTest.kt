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
        assertEquals(1_000L, plan.plannedBoundaryAlarm?.triggerAtEpochMillis)
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
        assertEquals(1_000L, trigger)
    }

    private fun baseInput(
        isPracticeStarted: Boolean = true,
        isPaused: Boolean = false,
        nowEpochMillis: Long = 500L,
        currentOccurrence: NotificationOccurrenceSnapshot? = occurrence,
        notificationCapability: NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED,
        activeNotificationOccurrenceId: Long? = null,
    ): NotificationPlanningInput {
        return NotificationPlanningInput(
            isPracticeStarted = isPracticeStarted,
            isPaused = isPaused,
            nowEpochMillis = nowEpochMillis,
            currentOccurrence = currentOccurrence,
            notificationCapability = notificationCapability,
            activeNotificationOccurrenceId = activeNotificationOccurrenceId,
        )
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
