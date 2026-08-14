// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
package com.me4hik.praktika.notification

/** Pure PendingIntent identity helpers (no Android framework dependency). */
object PracticeAlarmIdentity {
    fun actionFor(alarm: BoundaryAlarmPlan): String {
        return "com.me4hik.praktika.action.PRACTICE_ALARM/" +
            "${alarm.eventType.name.lowercase()}/${alarm.occurrenceId}/${alarm.triggerAtEpochMillis}"
    }

    fun requestCodeFor(alarm: BoundaryAlarmPlan): Int = actionFor(alarm).hashCode()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
