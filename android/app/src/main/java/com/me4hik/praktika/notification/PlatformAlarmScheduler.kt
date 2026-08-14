// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - platform alarm boundary
package com.me4hik.praktika.notification

interface PlatformAlarmScheduler {
    fun scheduleAlarms(plan: NotificationPlan, previousAlarms: List<BoundaryAlarmPlan>)

    fun cancelAlarms(alarms: List<BoundaryAlarmPlan>)

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    fun scheduleResidualPlannedRecovery(
        recoveryPlan: BoundaryAlarmPlan,
        useAlarmClock: Boolean,
    ): ResidualPlannedRecoveryResult
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
}

// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
enum class ResidualPlannedRecoveryResult {
    ALARM_CLOCK_SCHEDULED,
    FALLBACK_AWI_SCHEDULED,
    FAILED_FALLBACK_AWI,
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
