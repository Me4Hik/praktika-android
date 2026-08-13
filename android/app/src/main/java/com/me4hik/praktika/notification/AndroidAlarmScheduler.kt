// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - AlarmManager scheduling
// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact scheduled reminder delivery
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics

enum class AlarmSchedulerApiMode {
    SET_WINDOW,
    SET_AND_ALLOW_WHILE_IDLE,
}

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
            )?.let {
                alarmManager.cancel(it)
                TargetedBugDiagnostics.recordAlarmCancelled(
                    occurrenceId = alarm.occurrenceId,
                    reason = "schedule_replace",
                )
            }
        }
    }

    private fun scheduleBoundary(alarm: BoundaryAlarmPlan) {
        val triggerAt = alarm.triggerAtEpochMillis
        val pendingIntent = pendingIntentFor(
            alarm,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let { alarmManager.cancel(it) }

        val operation = pendingIntentFor(
            alarm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        val capability = ExactAlarmCapabilityResolver.resolve(alarmManager)
        val scheduled = when (val override = experimentApiModeOverride) {
            AlarmSchedulerApiMode.SET_WINDOW -> scheduleSetWindow(triggerAt, operation)
            AlarmSchedulerApiMode.SET_AND_ALLOW_WHILE_IDLE -> scheduleAllowWhileIdle(triggerAt, operation)
            null -> when (alarm.eventType) {
                BoundaryEventType.PLANNED_BOUNDARY -> schedulePlannedReminder(triggerAt, operation, capability)
                BoundaryEventType.EXPIRY_BOUNDARY -> scheduleMaintenanceReminder(triggerAt, operation)
            }
        }

        TargetedBugDiagnostics.recordAlarmScheduled(
            occurrenceId = alarm.occurrenceId,
            alarmType = alarmTypeLabel(alarm.eventType),
            triggerAtEpochMs = triggerAt,
            windowMs = scheduled.windowMs,
            schedulerApi = scheduled.schedulerApi,
            exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(capability),
            reasonFromSync = TargetedBugDiagnostics.NotificationTraceContext.syncReason,
        )
    }

    private data class ScheduledApi(val schedulerApi: String, val windowMs: Long)

    private fun schedulePlannedReminder(
        triggerAt: Long,
        operation: PendingIntent,
        capability: ExactAlarmCapability,
    ): ScheduledApi {
        return when (capability) {
            ExactAlarmCapability.NOT_REQUIRED,
            ExactAlarmCapability.AVAILABLE,
            -> scheduleExactAndAllowWhileIdle(triggerAt, operation)
            ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED -> scheduleAllowWhileIdle(triggerAt, operation)
        }
    }

    private fun scheduleMaintenanceReminder(triggerAt: Long, operation: PendingIntent): ScheduledApi {
        return scheduleAllowWhileIdle(triggerAt, operation)
    }

    private fun scheduleExactAndAllowWhileIdle(triggerAt: Long, operation: PendingIntent): ScheduledApi {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation,
            )
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation,
            )
        }
        return ScheduledApi(schedulerApi = "setExactAndAllowWhileIdle", windowMs = 0L)
    }

    private fun scheduleAllowWhileIdle(triggerAt: Long, operation: PendingIntent): ScheduledApi {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation,
            )
        } else {
            @Suppress("DEPRECATION")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                operation,
            )
        }
        return ScheduledApi(schedulerApi = "setAndAllowWhileIdle", windowMs = 0L)
    }

    private fun scheduleSetWindow(triggerAt: Long, operation: PendingIntent): ScheduledApi {
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
        return ScheduledApi(schedulerApi = "setWindow", windowMs = WINDOW_LENGTH_MILLIS)
    }

    private fun alarmTypeLabel(eventType: BoundaryEventType): String {
        return when (eventType) {
            BoundaryEventType.PLANNED_BOUNDARY -> "planned"
            BoundaryEventType.EXPIRY_BOUNDARY -> "expiry"
        }
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
        @Volatile
        var experimentApiModeOverride: AlarmSchedulerApiMode? = null

        /** @deprecated Use [experimentApiModeOverride] for test harnesses. */
        @Deprecated("Use experimentApiModeOverride", ReplaceWith("experimentApiModeOverride"))
        var experimentApiMode: AlarmSchedulerApiMode
            get() = experimentApiModeOverride ?: AlarmSchedulerApiMode.SET_WINDOW
            set(value) {
                experimentApiModeOverride = value
            }

        const val WINDOW_LENGTH_MILLIS = 10 * 60 * 1000L

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
