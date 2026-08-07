// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - platform alarm boundary
package com.me4hik.praktika.notification

interface PlatformAlarmScheduler {
    fun scheduleAlarms(plan: NotificationPlan, previousAlarms: List<BoundaryAlarmPlan>)

    fun cancelAlarms(alarms: List<BoundaryAlarmPlan>)
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
