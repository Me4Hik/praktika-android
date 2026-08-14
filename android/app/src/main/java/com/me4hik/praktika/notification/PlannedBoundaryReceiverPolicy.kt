// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

data class PlannedBoundaryIntentSnapshot(
    val eventType: String?,
    val occurrenceId: Long,
    val semanticPlannedAtEpochMillis: Long,
    val triggerAtEpochMillis: Long,
    val intentAction: String?,
)

data class AuthoritativeOccurrenceSnapshot(
    val occurrenceId: Long,
    val status: QuestionOccurrenceStatus,
    val plannedAtEpochMillis: Long,
)

sealed interface PlannedBoundaryReceiverDisposition {
    data class Malformed(
        val decision: PlannedBoundarySchedulerDecision = PlannedBoundarySchedulerDecision.IGNORE_MALFORMED,
    ) : PlannedBoundaryReceiverDisposition

    data class Sync(
        val reason: NotificationSyncReason,
        val decision: PlannedBoundarySchedulerDecision,
    ) : PlannedBoundaryReceiverDisposition

    data class ResidualRecovery(
        val decision: PlannedBoundarySchedulerDecision,
        val recoveryPlan: BoundaryAlarmPlan,
        val useAlarmClock: Boolean,
    ) : PlannedBoundaryReceiverDisposition
}

object PlannedBoundaryReceiverPolicy {
    fun parseAndDecide(
        intent: PlannedBoundaryIntentSnapshot,
        authoritative: AuthoritativeOccurrenceSnapshot?,
        nowEpochMillis: Long,
        exactCapability: ExactAlarmCapability,
    ): PlannedBoundaryReceiverDisposition {
        val eventType = intent.eventType
        if (eventType.isNullOrBlank()) {
            return PlannedBoundaryReceiverDisposition.Malformed()
        }
        if (eventType == BoundaryEventType.EXPIRY_BOUNDARY.name) {
            return PlannedBoundaryReceiverDisposition.Sync(
                reason = NotificationSyncReason.EXPIRY_ALARM,
                decision = PlannedBoundarySchedulerDecision.NORMAL_REARM,
            )
        }
        if (eventType != BoundaryEventType.PLANNED_BOUNDARY.name) {
            return PlannedBoundaryReceiverDisposition.Malformed()
        }

        if (!isStructurallyValidPlannedIntent(intent)) {
            return PlannedBoundaryReceiverDisposition.Malformed()
        }

        val auth = authoritative
            ?: return PlannedBoundaryReceiverDisposition.Sync(
                reason = NotificationSyncReason.PLANNED_ALARM,
                decision = PlannedBoundarySchedulerDecision.NORMAL_REARM,
            )

        if (!matchesAuthoritative(intent, auth)) {
            return PlannedBoundaryReceiverDisposition.Sync(
                reason = NotificationSyncReason.PLANNED_ALARM,
                decision = PlannedBoundarySchedulerDecision.NORMAL_REARM,
            )
        }

        if (auth.status != QuestionOccurrenceStatus.SCHEDULED) {
            return PlannedBoundaryReceiverDisposition.Sync(
                reason = NotificationSyncReason.PLANNED_ALARM,
                decision = PlannedBoundarySchedulerDecision.POST_NOW,
            )
        }

        if (nowEpochMillis >= auth.plannedAtEpochMillis) {
            return PlannedBoundaryReceiverDisposition.Sync(
                reason = NotificationSyncReason.PLANNED_ALARM,
                decision = PlannedBoundarySchedulerDecision.POST_NOW,
            )
        }

        val delta = auth.plannedAtEpochMillis - nowEpochMillis
        if (delta > PlannedBoundaryTiming.MAX_RESIDUAL_EARLY_RECOVERY_MS) {
            return PlannedBoundaryReceiverDisposition.Sync(
                reason = NotificationSyncReason.PLANNED_ALARM,
                decision = PlannedBoundarySchedulerDecision.EARLY_OUTSIDE_RECOVERY_BOUND,
            )
        }

        val recoveryPlan = BoundaryAlarmPlan(
            occurrenceId = auth.occurrenceId,
            eventType = BoundaryEventType.PLANNED_BOUNDARY,
            triggerAtEpochMillis = auth.plannedAtEpochMillis,
            plannedAtEpochMillis = auth.plannedAtEpochMillis,
        )
        return when (exactCapability) {
            ExactAlarmCapability.NOT_REQUIRED,
            ExactAlarmCapability.AVAILABLE,
            -> PlannedBoundaryReceiverDisposition.ResidualRecovery(
                decision = PlannedBoundarySchedulerDecision.ALARM_CLOCK_RECOVERY,
                recoveryPlan = recoveryPlan,
                useAlarmClock = true,
            )
            ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED ->
                PlannedBoundaryReceiverDisposition.ResidualRecovery(
                    decision = PlannedBoundarySchedulerDecision.RESIDUAL_EARLY_FALLBACK_AWI,
                    recoveryPlan = recoveryPlan,
                    useAlarmClock = false,
                )
        }
    }

    fun isStructurallyValidPlannedIntent(intent: PlannedBoundaryIntentSnapshot): Boolean {
        if (intent.eventType != BoundaryEventType.PLANNED_BOUNDARY.name) {
            return false
        }
        if (intent.occurrenceId <= 0L) {
            return false
        }
        if (intent.semanticPlannedAtEpochMillis <= 0L) {
            return false
        }
        if (intent.triggerAtEpochMillis <= 0L) {
            return false
        }
        val expectedAction = PracticeAlarmIdentity.actionFor(
            BoundaryAlarmPlan(
                occurrenceId = intent.occurrenceId,
                eventType = BoundaryEventType.PLANNED_BOUNDARY,
                triggerAtEpochMillis = intent.triggerAtEpochMillis,
                plannedAtEpochMillis = intent.semanticPlannedAtEpochMillis,
            ),
        )
        if (intent.intentAction != null && intent.intentAction != expectedAction) {
            return false
        }
        val biasedOk = PlannedBoundaryTiming.isExpectedBiasedTrigger(
            intent.semanticPlannedAtEpochMillis,
            intent.triggerAtEpochMillis,
        )
        val recoveryOk = PlannedBoundaryTiming.isExpectedRecoveryTrigger(
            intent.semanticPlannedAtEpochMillis,
            intent.triggerAtEpochMillis,
        )
        return biasedOk || recoveryOk
    }

    fun matchesAuthoritative(
        intent: PlannedBoundaryIntentSnapshot,
        authoritative: AuthoritativeOccurrenceSnapshot,
    ): Boolean {
        if (intent.occurrenceId != authoritative.occurrenceId) {
            return false
        }
        if (intent.semanticPlannedAtEpochMillis != authoritative.plannedAtEpochMillis) {
            return false
        }
        return PlannedBoundaryTiming.isExpectedBiasedTrigger(
            authoritative.plannedAtEpochMillis,
            intent.triggerAtEpochMillis,
        ) || PlannedBoundaryTiming.isExpectedRecoveryTrigger(
            authoritative.plannedAtEpochMillis,
            intent.triggerAtEpochMillis,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
