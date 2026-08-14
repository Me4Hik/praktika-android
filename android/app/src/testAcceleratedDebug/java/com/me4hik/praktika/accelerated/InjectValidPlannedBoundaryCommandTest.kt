// 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import android.content.Context
import android.content.Intent
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.BoundaryEventType
import com.me4hik.praktika.notification.PlannedBoundaryTiming
import com.me4hik.praktika.notification.PracticeAlarmIdentity
import com.me4hik.praktika.notification.PracticeAlarmReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class InjectValidPlannedBoundaryCommandTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val wallNow = 1_700_000_000_000L
    private val elapsed = 55_000L
    private val occurrenceId = 42L

    @Test
    fun successAtTMinus12_dispatchesCanonicalIntentOnce() {
        val plannedAt = wallNow + 12_000L
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(scheduled(plannedAt)),
            exactAlarmCapability = "available",
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )

        assertEquals(InjectValidPlannedBoundaryCommand.STATUS_VALID_INTENT_DISPATCHED, result.status)
        assertEquals(occurrenceId, result.occurrenceId)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, result.occurrenceStatusBefore)
        assertEquals(plannedAt, result.plannedAtMs)
        assertEquals(plannedAt + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS, result.biasedTriggerAtMs)
        assertEquals(-12_000L, result.deltaToPlannedMs)
        assertEquals(1, broadcasts.size)

        val intent = broadcasts.single()
        val expectedAction = PracticeAlarmIdentity.actionFor(
            BoundaryAlarmPlan(
                occurrenceId = occurrenceId,
                eventType = BoundaryEventType.PLANNED_BOUNDARY,
                triggerAtEpochMillis = plannedAt + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS,
                plannedAtEpochMillis = plannedAt,
            ),
        )
        assertEquals(expectedAction, intent.action)
        assertEquals(expectedAction, result.intentAction)
        assertEquals(PracticeAlarmReceiver::class.java.name, intent.component!!.className)
        assertEquals(occurrenceId, intent.getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L))
        assertEquals(
            BoundaryEventType.PLANNED_BOUNDARY.name,
            intent.getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE),
        )
        assertEquals(
            plannedAt + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS,
            intent.getLongExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, -1L),
        )
        assertEquals(
            plannedAt,
            intent.getLongExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, -1L),
        )
        assertEquals("available", result.exactAlarmCapability)
    }

    @Test
    fun hostIdentityExtrasAreNotPartOfCommandApi_roomIdentityWins() {
        // Simulate hostile/erroneous ADB extras that must never feed evaluate/execute.
        val hostile = Intent().apply {
            putExtra("occurrence_id", 999L)
            putExtra("planned_at", 1L)
            putExtra("trigger_at", 2L)
            putExtra("event_type", "EXPIRY_BOUNDARY")
            putExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, 999L)
            putExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, 1L)
            putExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, 2L)
            putExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE, "EXPIRY_BOUNDARY")
        }
        assertTrue(hostile.hasExtra("occurrence_id"))

        val plannedAt = wallNow + 12_000L
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(scheduled(plannedAt)),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        // Identity from Room snapshot only — hostile Intent never passed into execute.
        assertEquals(occurrenceId, broadcasts.single().getLongExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, -1L))
        assertEquals(plannedAt, result.plannedAtMs)
        assertEquals(BoundaryEventType.PLANNED_BOUNDARY.name,
            broadcasts.single().getStringExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE))
    }

    @Test
    fun wrongPackage_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(scheduled(wallNow + 12_000L)),
            packageName = "com.me4hik.praktika",
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(InjectValidPlannedBoundaryCommand.STATUS_REJECTED_WRONG_PACKAGE, result.status)
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun emptyIncomplete_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = emptyList(),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(
            InjectValidPlannedBoundaryCommand.STATUS_REJECTED_NO_SCHEDULED_OCCURRENCE,
            result.status,
        )
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun ambiguousIncomplete_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(
                scheduled(wallNow + 12_000L),
                scheduled(wallNow + 12_000L).copy(id = 43L),
            ),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(
            InjectValidPlannedBoundaryCommand.STATUS_REJECTED_AMBIGUOUS_OCCURRENCE,
            result.status,
        )
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun availableStatus_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(
                InjectValidPlannedBoundaryCommand.IncompleteOccurrence(
                    id = occurrenceId,
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    plannedAtEpochMillis = wallNow + 12_000L,
                ),
            ),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(
            InjectValidPlannedBoundaryCommand.STATUS_REJECTED_OCCURRENCE_NOT_SCHEDULED,
            result.status,
        )
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun missedStatus_rejectsWithoutBroadcast() {
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(
                InjectValidPlannedBoundaryCommand.IncompleteOccurrence(
                    id = occurrenceId,
                    status = QuestionOccurrenceStatus.MISSED_BY_TIME,
                    plannedAtEpochMillis = wallNow + 12_000L,
                ),
            ),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(
            InjectValidPlannedBoundaryCommand.STATUS_REJECTED_OCCURRENCE_NOT_SCHEDULED,
            result.status,
        )
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun injectionBandBoundaries() {
        assertBand(-15_001L, accept = false)
        assertBand(-15_000L, accept = true)
        assertBand(-12_000L, accept = true)
        assertBand(-8_000L, accept = true)
        assertBand(-7_999L, accept = false)
        assertBand(0L, accept = false)
        assertBand(1_000L, accept = false)
    }

    @Test
    fun canonicalActionMatchesPracticeAlarmIdentityHelper() {
        val plannedAt = wallNow + 12_000L
        val biased = PlannedBoundaryTiming.biasedTriggerAt(plannedAt)
        val expected = PracticeAlarmIdentity.actionFor(
            BoundaryAlarmPlan(
                occurrenceId = occurrenceId,
                eventType = BoundaryEventType.PLANNED_BOUNDARY,
                triggerAtEpochMillis = biased,
                plannedAtEpochMillis = plannedAt,
            ),
        )
        val ready = InjectValidPlannedBoundaryCommand.evaluate(
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            incomplete = listOf(scheduled(plannedAt)),
        )
        assertEquals(expected, ready.intentAction)
        val intent = InjectValidPlannedBoundaryCommand.buildCanonicalIntent(context, ready)
        assertEquals(expected, intent.action)
    }

    @Test
    fun singleSuccessfulInvocation_sendBroadcastCountIsOne() {
        val broadcasts = mutableListOf<Intent>()
        InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(scheduled(wallNow + 12_000L)),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        assertEquals(1, broadcasts.size)
    }

    @Test
    fun harnessSurfaceDoesNotExposeRoomOrAlarmManagerHooks() {
        val methods = InjectValidPlannedBoundaryCommand::class.java.declaredMethods.map { it.name }
        assertFalse(methods.any { it.contains("AlarmManager", ignoreCase = true) })
        assertFalse(methods.any { it.contains("reconcile", ignoreCase = true) })
        assertFalse(methods.any { it.contains("updateOccurrence", ignoreCase = true) })
        assertFalse(methods.any { it.contains("schedule", ignoreCase = true) })
        assertTrue(methods.contains("execute"))
        assertTrue(methods.contains("evaluate"))
        assertTrue(methods.contains("buildCanonicalIntent"))
    }

    @Test
    fun commandNameIsInjectValidPlannedBoundary() {
        assertEquals("inject_valid_planned_boundary", InjectValidPlannedBoundaryCommand.COMMAND)
    }

    @Test
    fun rejectionDoesNotAttachDispatchedIntent() {
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = emptyList(),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { error("must not dispatch") },
        )
        assertNull(result.dispatchedIntent)
        assertNotNull(result.wallNowMs)
    }

    private fun assertBand(deltaToPlannedMs: Long, accept: Boolean) {
        // delta = wallNow - plannedAt  =>  plannedAt = wallNow - delta
        val plannedAt = wallNow - deltaToPlannedMs
        val broadcasts = mutableListOf<Intent>()
        val result = InjectValidPlannedBoundaryCommand.execute(
            context = context,
            incomplete = listOf(scheduled(plannedAt)),
            packageName = InjectValidPlannedBoundaryCommand.REQUIRED_PACKAGE,
            wallNowMs = wallNow,
            elapsedRealtimeMs = elapsed,
            sendBroadcast = { broadcasts.add(it) },
        )
        if (accept) {
            assertEquals(
                "delta=$deltaToPlannedMs should accept",
                InjectValidPlannedBoundaryCommand.STATUS_VALID_INTENT_DISPATCHED,
                result.status,
            )
            assertEquals(1, broadcasts.size)
            assertEquals(deltaToPlannedMs, result.deltaToPlannedMs)
        } else {
            assertEquals(
                "delta=$deltaToPlannedMs should reject",
                InjectValidPlannedBoundaryCommand.STATUS_REJECTED_OUTSIDE_INJECTION_BAND,
                result.status,
            )
            assertTrue(broadcasts.isEmpty())
        }
    }

    private fun scheduled(plannedAt: Long) =
        InjectValidPlannedBoundaryCommand.IncompleteOccurrence(
            id = occurrenceId,
            status = QuestionOccurrenceStatus.SCHEDULED,
            plannedAtEpochMillis = plannedAt,
        )
}
// 14.08.2026 Accelerated planned-boundary injection harness cursor by Me4Hik END
