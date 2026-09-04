// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START - canonical alarm Intent builder
package com.me4hik.praktika.notification

import android.content.Context
import android.content.Intent

/**
 * Single construction site for [PracticeAlarmReceiver] broadcast Intents
 * (AlarmManager PendingIntent path and accelerated inject harness).
 */
object PracticeAlarmIntents {
    fun buildReceiverIntent(context: Context, alarm: BoundaryAlarmPlan): Intent {
        return Intent(context, PracticeAlarmReceiver::class.java).apply {
            action = PracticeAlarmIdentity.actionFor(alarm)
            putExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, alarm.occurrenceId)
            putExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE, alarm.eventType.name)
            putExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, alarm.triggerAtEpochMillis)
            putExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, alarm.plannedAtEpochMillis)
        }
    }
}
// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END
