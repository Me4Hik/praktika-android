// 04.09.2026 Exact expiry/deferred capability gating cursor by Me4Hik START
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AndroidAlarmSchedulerExactCapabilityHostTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var scheduler: AndroidAlarmScheduler

    @Before
    fun setUp() {
        AndroidAlarmScheduler.experimentApiModeOverride = null
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        scheduler = AndroidAlarmScheduler(context)
    }

    @After
    fun tearDown() {
        AndroidAlarmScheduler.experimentApiModeOverride = null
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun expiry_exactWhenCapabilityAvailable() {
        setCanScheduleExactAlarms(true)
        scheduleOnly(
            BoundaryAlarmPlan(
                occurrenceId = 82L,
                eventType = BoundaryEventType.EXPIRY_BOUNDARY,
                triggerAtEpochMillis = 2_000L,
                plannedAtEpochMillis = 1_000L,
            ),
        )
        assertExactAllowWhileIdleScheduled(triggerAt = 2_000L)
    }

    @Test
    fun expiry_awiFallbackWhenExactUnavailable() {
        setCanScheduleExactAlarms(false)
        scheduleOnly(
            BoundaryAlarmPlan(
                occurrenceId = 82L,
                eventType = BoundaryEventType.EXPIRY_BOUNDARY,
                triggerAtEpochMillis = 2_000L,
                plannedAtEpochMillis = 1_000L,
            ),
        )
        assertInexactAllowWhileIdleScheduled(triggerAt = 2_000L)
    }

    @Test
    fun deferred_exactWhenCapabilityAvailable() {
        setCanScheduleExactAlarms(true)
        scheduleOnly(
            BoundaryAlarmPlan(
                occurrenceId = 82L,
                eventType = BoundaryEventType.DEFERRED_REMINDER,
                triggerAtEpochMillis = 1_500L,
                plannedAtEpochMillis = 1_000L,
            ),
        )
        assertExactAllowWhileIdleScheduled(triggerAt = 1_500L)
    }

    @Test
    fun deferred_awiFallbackWhenExactUnavailable() {
        setCanScheduleExactAlarms(false)
        scheduleOnly(
            BoundaryAlarmPlan(
                occurrenceId = 82L,
                eventType = BoundaryEventType.DEFERRED_REMINDER,
                triggerAtEpochMillis = 1_500L,
                plannedAtEpochMillis = 1_000L,
            ),
        )
        assertInexactAllowWhileIdleScheduled(triggerAt = 1_500L)
    }

    @Test
    fun planned_exactWhenCapabilityAvailable_unchanged() {
        setCanScheduleExactAlarms(true)
        val plannedAt = 1_000L
        val trigger = PlannedBoundaryTiming.biasedTriggerAt(plannedAt)
        scheduleOnly(
            BoundaryAlarmPlan(
                occurrenceId = 81L,
                eventType = BoundaryEventType.PLANNED_BOUNDARY,
                triggerAtEpochMillis = trigger,
                plannedAtEpochMillis = plannedAt,
            ),
        )
        assertExactAllowWhileIdleScheduled(triggerAt = trigger)
    }

    @Test
    fun planned_awiFallbackWhenExactUnavailable_unchanged() {
        setCanScheduleExactAlarms(false)
        val plannedAt = 1_000L
        val trigger = PlannedBoundaryTiming.biasedTriggerAt(plannedAt)
        scheduleOnly(
            BoundaryAlarmPlan(
                occurrenceId = 81L,
                eventType = BoundaryEventType.PLANNED_BOUNDARY,
                triggerAtEpochMillis = trigger,
                plannedAtEpochMillis = plannedAt,
            ),
        )
        assertInexactAllowWhileIdleScheduled(triggerAt = trigger)
    }

    private fun setCanScheduleExactAlarms(allowed: Boolean) {
        ShadowAlarmManager.setCanScheduleExactAlarms(allowed)
        assertEquals(
            if (allowed) ExactAlarmCapability.AVAILABLE else ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED,
            ExactAlarmCapabilityResolver.resolve(alarmManager),
        )
    }

    private fun scheduleOnly(alarm: BoundaryAlarmPlan) {
        val plan = NotificationPlan(
            cancelAllAlarms = false,
            plannedBoundaryAlarm = alarm.takeIf { it.eventType == BoundaryEventType.PLANNED_BOUNDARY },
            expiryBoundaryAlarm = alarm.takeIf { it.eventType == BoundaryEventType.EXPIRY_BOUNDARY },
            deferredReminderAlarm = alarm.takeIf { it.eventType == BoundaryEventType.DEFERRED_REMINDER },
            cancelNotification = false,
            showNotification = null,
        )
        scheduler.scheduleAlarms(plan, previousAlarms = emptyList())
    }

    private fun assertExactAllowWhileIdleScheduled(triggerAt: Long) {
        val scheduled = shadowAlarmManager.scheduledAlarms.single {
            it.triggerAtMs == triggerAt
        }
        assertTrue(scheduled.isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, scheduled.windowLengthMs)
    }

    private fun assertInexactAllowWhileIdleScheduled(triggerAt: Long) {
        val scheduled = shadowAlarmManager.scheduledAlarms.single {
            it.triggerAtMs == triggerAt
        }
        assertTrue(scheduled.isAllowWhileIdle)
        assertTrue(
            "expected inexact window, got ${scheduled.windowLengthMs}",
            scheduled.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT,
        )
    }
}

class NotificationPlannerAvailableNoNextPlannedTest {
    @Test
    fun available_doesNotPlanNextPlannedBoundary() {
        val occurrence = NotificationOccurrenceSnapshot(
            occurrenceId = 82L,
            status = QuestionOccurrenceStatus.AVAILABLE,
            plannedAtEpochMillis = 1_000L,
            availableUntilEpochMillis = 2_000L,
            questionTextSnapshot = "Q",
            openedAtEpochMillis = null,
        )
        val plan = NotificationPlanner.plan(
            input = NotificationPlanningInput(
                isPracticeStarted = true,
                isPaused = false,
                nowEpochMillis = 1_500L,
                currentOccurrence = occurrence,
                notificationCapability = NotificationDeliveryCapability.ENABLED,
                activeNotificationOccurrenceId = null,
                activeNotificationKind = null,
            ),
            soundEnabled = true,
        )
        assertNull(plan.plannedBoundaryAlarm)
        assertEquals(BoundaryEventType.EXPIRY_BOUNDARY, plan.expiryBoundaryAlarm?.eventType)
        assertEquals(2_000L, plan.expiryBoundaryAlarm?.triggerAtEpochMillis)
        assertNull(plan.deferredReminderAlarm)
    }
}
// 04.09.2026 Exact expiry/deferred capability gating cursor by Me4Hik END
