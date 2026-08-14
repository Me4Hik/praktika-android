// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
package com.me4hik.praktika.notification

enum class PlannedBoundarySchedulerDecision {
    BIAS_SCHEDULE,
    POST_NOW,
    ALARM_CLOCK_RECOVERY,
    RESIDUAL_EARLY_FALLBACK_AWI,
    EARLY_OUTSIDE_RECOVERY_BOUND,
    NORMAL_REARM,
    IGNORE_MALFORMED,
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
