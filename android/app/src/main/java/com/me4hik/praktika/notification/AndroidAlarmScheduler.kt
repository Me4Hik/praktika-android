// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - AlarmManager scheduling
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidAlarmScheduler(
    private val context: Context,
) : PlatformAlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleAlarms(plan: NotificationPlan, previousAlarms: List<BoundaryAlarmPlan>) {
        cancelAlarms(previousAlarms)
        if (plan.cancelAllAlarms) {
            return
        }
        plan.plannedBoundaryAlarm?.let { scheduleBoundary(it) }
        plan.expiryBoundaryAlarm?.let { scheduleBoundary(it) }
    }

    override fun cancelAlarms(alarms: List<BoundaryAlarmPlan>) {
        alarms.forEach { alarm ->
            pendingIntentFor(
                alarm,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleAlarmsLegacy(plan: NotificationPlan) {
        scheduleAlarms(plan, emptyList())
    }

    private fun scheduleBoundary(alarm: BoundaryAlarmPlan) {
        val triggerAt = alarm.triggerAtEpochMillis
        // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik START - FLAG_IMMUTABLE for FLAG_NO_CREATE lookup
        val pendingIntent = pendingIntentFor(
            alarm,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik END
        pendingIntent?.let { alarmManager.cancel(it) }

        val operation = pendingIntentFor(
            alarm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                WINDOW_LENGTH_MILLIS,
                operation,
            )
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                WINDOW_LENGTH_MILLIS,
                operation,
            )
        }
    }

    private fun cancelPendingAction(
        eventType: BoundaryEventType,
        occurrenceId: Long,
        boundaryEpochMillis: Long,
    ) {
        pendingIntentFor(
            BoundaryAlarmPlan(
                occurrenceId = occurrenceId,
                eventType = eventType,
                triggerAtEpochMillis = boundaryEpochMillis,
                plannedAtEpochMillis = boundaryEpochMillis,
            ),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { alarmManager.cancel(it) }
    }

    private fun pendingIntentFor(
        alarm: BoundaryAlarmPlan,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, PracticeAlarmReceiver::class.java).apply {
            action = actionFor(alarm)
            putExtra(PracticeAlarmReceiver.EXTRA_OCCURRENCE_ID, alarm.occurrenceId)
            putExtra(PracticeAlarmReceiver.EXTRA_EVENT_TYPE, alarm.eventType.name)
            putExtra(PracticeAlarmReceiver.EXTRA_BOUNDARY_EPOCH_MILLIS, alarm.triggerAtEpochMillis)
            putExtra(PracticeAlarmReceiver.EXTRA_PLANNED_AT_EPOCH_MILLIS, alarm.plannedAtEpochMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarm),
            intent,
            flags,
        )
    }

    companion object {
        const val WINDOW_LENGTH_MILLIS = 10 * 60 * 1000L
        private const val PLACEHOLDER_OCCURRENCE_ID = -1L
        private const val PLACEHOLDER_BOUNDARY_EPOCH = 0L

        fun actionFor(alarm: BoundaryAlarmPlan): String {
            return "com.me4hik.praktika.action.PRACTICE_ALARM/" +
                "${alarm.eventType.name.lowercase()}/${alarm.occurrenceId}/${alarm.triggerAtEpochMillis}"
        }

        fun requestCodeFor(alarm: BoundaryAlarmPlan): Int {
            return actionFor(alarm).hashCode()
        }
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
