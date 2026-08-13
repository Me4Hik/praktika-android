// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact scheduled reminder delivery
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.os.Build

object ExactAlarmCapabilityResolver {
    fun resolve(alarmManager: AlarmManager): ExactAlarmCapability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ExactAlarmCapability.NOT_REQUIRED
        }
        return if (alarmManager.canScheduleExactAlarms()) {
            ExactAlarmCapability.AVAILABLE
        } else {
            ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED
        }
    }

    fun diagnosticLabel(capability: ExactAlarmCapability): String {
        return when (capability) {
            ExactAlarmCapability.NOT_REQUIRED,
            ExactAlarmCapability.AVAILABLE,
            -> "available"
            ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED -> "required"
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
