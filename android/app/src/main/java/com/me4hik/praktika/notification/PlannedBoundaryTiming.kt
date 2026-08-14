// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
package com.me4hik.praktika.notification

/**
 * Transport-only timing for PLANNED_BOUNDARY AlarmManager delivery.
 * Does not change Room semantic plannedAt / availability.
 */
object PlannedBoundaryTiming {
    const val PLANNED_BOUNDARY_TRIGGER_BIAS_MS = 15_000L
    const val MAX_RESIDUAL_EARLY_RECOVERY_MS = 30_000L

    fun biasedTriggerAt(semanticPlannedAtEpochMillis: Long): Long =
        semanticPlannedAtEpochMillis + PLANNED_BOUNDARY_TRIGGER_BIAS_MS

    fun isExpectedBiasedTrigger(
        semanticPlannedAtEpochMillis: Long,
        triggerAtEpochMillis: Long,
    ): Boolean = triggerAtEpochMillis == biasedTriggerAt(semanticPlannedAtEpochMillis)

    fun isExpectedRecoveryTrigger(
        semanticPlannedAtEpochMillis: Long,
        triggerAtEpochMillis: Long,
    ): Boolean = triggerAtEpochMillis == semanticPlannedAtEpochMillis
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
