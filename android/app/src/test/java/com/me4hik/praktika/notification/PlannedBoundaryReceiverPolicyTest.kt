// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedBoundaryReceiverPolicyTest {
    private val plannedAt = 1_000_000L
    private val biasedTrigger = plannedAt + PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS
    private val occurrenceId = 7L

    private val authoritative = AuthoritativeOccurrenceSnapshot(
        occurrenceId = occurrenceId,
        status = QuestionOccurrenceStatus.SCHEDULED,
        plannedAtEpochMillis = plannedAt,
    )

    @Test
    fun biasConstantIsFifteenSeconds() {
        assertEquals(15_000L, PlannedBoundaryTiming.PLANNED_BOUNDARY_TRIGGER_BIAS_MS)
        assertEquals(30_000L, PlannedBoundaryTiming.MAX_RESIDUAL_EARLY_RECOVERY_MS)
    }

    @Test
    fun knownTMinus12_exactAvailable_schedulesAlarmClockRecoveryWithoutAwiRearm() {
        val disposition = decide(
            now = plannedAt - 12_000L,
            exact = ExactAlarmCapability.AVAILABLE,
        )
        val recovery = disposition as PlannedBoundaryReceiverDisposition.ResidualRecovery
        assertEquals(PlannedBoundarySchedulerDecision.ALARM_CLOCK_RECOVERY, recovery.decision)
        assertTrue(recovery.useAlarmClock)
        assertEquals(plannedAt, recovery.recoveryPlan.triggerAtEpochMillis)
        assertEquals(plannedAt, recovery.recoveryPlan.plannedAtEpochMillis)
        assertEquals(BoundaryEventType.PLANNED_BOUNDARY, recovery.recoveryPlan.eventType)
    }

    @Test
    fun residualEarly_exactUnavailable_usesFallbackAwi() {
        val disposition = decide(
            now = plannedAt - 12_000L,
            exact = ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED,
        )
        val recovery = disposition as PlannedBoundaryReceiverDisposition.ResidualRecovery
        assertEquals(PlannedBoundarySchedulerDecision.RESIDUAL_EARLY_FALLBACK_AWI, recovery.decision)
        assertFalse(recovery.useAlarmClock)
    }

    @Test
    fun earlyOutsideBound_syncsWithoutRecovery() {
        val disposition = decide(
            now = plannedAt - 60_000L,
            exact = ExactAlarmCapability.AVAILABLE,
        )
        val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
        assertEquals(PlannedBoundarySchedulerDecision.EARLY_OUTSIDE_RECOVERY_BOUND, sync.decision)
        assertEquals(NotificationSyncReason.PLANNED_ALARM, sync.reason)
    }

    @Test
    fun earlyAtExactly30s_isInsideRecoveryBound() {
        val disposition = decide(
            now = plannedAt - 30_000L,
            exact = ExactAlarmCapability.AVAILABLE,
        )
        assertTrue(disposition is PlannedBoundaryReceiverDisposition.ResidualRecovery)
    }

    @Test
    fun earlyAt15s_and1ms_recover() {
        listOf(15_000L, 1L).forEach { earlyBy ->
            val disposition = decide(
                now = plannedAt - earlyBy,
                exact = ExactAlarmCapability.AVAILABLE,
            )
            assertTrue(
                "earlyBy=$earlyBy",
                disposition is PlannedBoundaryReceiverDisposition.ResidualRecovery,
            )
        }
    }

    @Test
    fun atOrAfterPlannedAt_postsNow() {
        listOf(0L, 1L, 30_000L, 5 * 60_000L).forEach { after ->
            val disposition = decide(
                now = plannedAt + after,
                exact = ExactAlarmCapability.AVAILABLE,
            )
            val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
            assertEquals(PlannedBoundarySchedulerDecision.POST_NOW, sync.decision)
        }
    }

    @Test
    fun recoveryCallbackIdentity_atPlannedAt_postsNow() {
        val recoveryPlan = BoundaryAlarmPlan(
            occurrenceId = occurrenceId,
            eventType = BoundaryEventType.PLANNED_BOUNDARY,
            triggerAtEpochMillis = plannedAt,
            plannedAtEpochMillis = plannedAt,
        )
        val intent = PlannedBoundaryIntentSnapshot(
            eventType = BoundaryEventType.PLANNED_BOUNDARY.name,
            occurrenceId = occurrenceId,
            semanticPlannedAtEpochMillis = plannedAt,
            triggerAtEpochMillis = plannedAt,
            intentAction = PracticeAlarmIdentity.actionFor(recoveryPlan),
        )
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = intent,
            authoritative = authoritative,
            nowEpochMillis = plannedAt,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
        assertEquals(PlannedBoundarySchedulerDecision.POST_NOW, sync.decision)
    }

    @Test
    fun malformedUnknownEventType_ignored() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent().copy(eventType = "NOT_A_BOUNDARY"),
            authoritative = authoritative,
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        assertTrue(disposition is PlannedBoundaryReceiverDisposition.Malformed)
    }

    @Test
    fun malformedMissingEventType_ignored() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent().copy(eventType = null),
            authoritative = authoritative,
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        assertTrue(disposition is PlannedBoundaryReceiverDisposition.Malformed)
    }

    @Test
    fun malformedOccurrenceId_ignored() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent().copy(occurrenceId = -1L),
            authoritative = authoritative,
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        assertTrue(disposition is PlannedBoundaryReceiverDisposition.Malformed)
    }

    @Test
    fun wrongOccurrenceId_doesNotRecover() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent(),
            authoritative = authoritative.copy(occurrenceId = 99L),
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
        assertEquals(PlannedBoundarySchedulerDecision.NORMAL_REARM, sync.decision)
    }

    @Test
    fun plannedAtMismatch_doesNotRecover() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent(),
            authoritative = authoritative.copy(plannedAtEpochMillis = plannedAt + 1_000L),
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
        assertEquals(PlannedBoundarySchedulerDecision.NORMAL_REARM, sync.decision)
    }

    @Test
    fun triggerMismatch_malformed() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent().copy(triggerAtEpochMillis = plannedAt + 1_000L),
            authoritative = authoritative,
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        assertTrue(disposition is PlannedBoundaryReceiverDisposition.Malformed)
    }

    @Test
    fun wrongAction_malformed() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent().copy(intentAction = "com.me4hik.praktika.action.WRONG"),
            authoritative = authoritative,
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        assertTrue(disposition is PlannedBoundaryReceiverDisposition.Malformed)
    }

    @Test
    fun expiryEvent_mapsToExpirySync() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent().copy(eventType = BoundaryEventType.EXPIRY_BOUNDARY.name),
            authoritative = authoritative,
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
        assertEquals(NotificationSyncReason.EXPIRY_ALARM, sync.reason)
    }

    @Test
    fun availableStatus_postsNowWithoutRecovery() {
        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent(),
            authoritative = authoritative.copy(status = QuestionOccurrenceStatus.AVAILABLE),
            nowEpochMillis = plannedAt - 12_000L,
            exactCapability = ExactAlarmCapability.AVAILABLE,
        )
        val sync = disposition as PlannedBoundaryReceiverDisposition.Sync
        assertEquals(PlannedBoundarySchedulerDecision.POST_NOW, sync.decision)
    }

    private fun decide(
        now: Long,
        exact: ExactAlarmCapability,
    ): PlannedBoundaryReceiverDisposition {
        return PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = biasedIntent(),
            authoritative = authoritative,
            nowEpochMillis = now,
            exactCapability = exact,
        )
    }

    private fun biasedIntent(): PlannedBoundaryIntentSnapshot {
        val plan = BoundaryAlarmPlan(
            occurrenceId = occurrenceId,
            eventType = BoundaryEventType.PLANNED_BOUNDARY,
            triggerAtEpochMillis = biasedTrigger,
            plannedAtEpochMillis = plannedAt,
        )
        return PlannedBoundaryIntentSnapshot(
            eventType = BoundaryEventType.PLANNED_BOUNDARY.name,
            occurrenceId = occurrenceId,
            semanticPlannedAtEpochMillis = plannedAt,
            triggerAtEpochMillis = biasedTrigger,
            intentAction = PracticeAlarmIdentity.actionFor(plan),
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
