// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - AlarmManager scheduling
// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact scheduled reminder delivery
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.me4hik.praktika.MainActivity
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
        plan.deferredReminderAlarm?.let { scheduleBoundary(it) }
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
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
            cancelSiblingPlannedIdentities(alarm)
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    override fun scheduleResidualPlannedRecovery(
        recoveryPlan: BoundaryAlarmPlan,
        useAlarmClock: Boolean,
    ): ResidualPlannedRecoveryResult {
        require(recoveryPlan.eventType == BoundaryEventType.PLANNED_BOUNDARY)
        require(
            PlannedBoundaryTiming.isExpectedRecoveryTrigger(
                recoveryPlan.plannedAtEpochMillis,
                recoveryPlan.triggerAtEpochMillis,
            ),
        )
        cancelSiblingPlannedIdentities(recoveryPlan)
        val operation = pendingIntentFor(
            recoveryPlan,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return ResidualPlannedRecoveryResult.FAILED_FALLBACK_AWI

        val capability = ExactAlarmCapabilityResolver.resolve(alarmManager)
        return if (useAlarmClock) {
            try {
                val scheduled = scheduleAlarmClock(recoveryPlan.triggerAtEpochMillis, operation)
                recordScheduled(
                    alarm = recoveryPlan,
                    scheduled = scheduled,
                    capability = capability,
                    decision = PlannedBoundarySchedulerDecision.ALARM_CLOCK_RECOVERY,
                )
                ResidualPlannedRecoveryResult.ALARM_CLOCK_SCHEDULED
            } catch (securityException: SecurityException) {
                val scheduled = scheduleAllowWhileIdle(recoveryPlan.triggerAtEpochMillis, operation)
                recordScheduled(
                    alarm = recoveryPlan,
                    scheduled = scheduled,
                    capability = capability,
                    decision = PlannedBoundarySchedulerDecision.RESIDUAL_EARLY_FALLBACK_AWI,
                )
                ResidualPlannedRecoveryResult.FALLBACK_AWI_SCHEDULED
            }
        } else {
            val scheduled = scheduleAllowWhileIdle(recoveryPlan.triggerAtEpochMillis, operation)
            recordScheduled(
                alarm = recoveryPlan,
                scheduled = scheduled,
                capability = capability,
                decision = PlannedBoundarySchedulerDecision.RESIDUAL_EARLY_FALLBACK_AWI,
            )
            ResidualPlannedRecoveryResult.FALLBACK_AWI_SCHEDULED
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private fun scheduleBoundary(alarm: BoundaryAlarmPlan) {
        val triggerAt = alarm.triggerAtEpochMillis
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        cancelSiblingPlannedIdentities(alarm)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
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
                // PLANNED / EXPIRY / DEFERRED share exact-when-capable scheduling (OPTION A:
                // expiry must not stay forever on inexact AWI while exact capability is available).
                BoundaryEventType.PLANNED_BOUNDARY,
                BoundaryEventType.EXPIRY_BOUNDARY,
                BoundaryEventType.DEFERRED_REMINDER,
                -> scheduleCapabilityGatedReminder(triggerAt, operation, capability)
            }
        }

        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        val decision = when (alarm.eventType) {
            BoundaryEventType.PLANNED_BOUNDARY -> PlannedBoundarySchedulerDecision.BIAS_SCHEDULE
            BoundaryEventType.EXPIRY_BOUNDARY -> PlannedBoundarySchedulerDecision.NORMAL_REARM
            BoundaryEventType.DEFERRED_REMINDER -> PlannedBoundarySchedulerDecision.NORMAL_REARM
        }
        recordScheduled(alarm, scheduled, capability, decision)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    private fun recordScheduled(
        alarm: BoundaryAlarmPlan,
        scheduled: ScheduledApi,
        capability: ExactAlarmCapability,
        decision: PlannedBoundarySchedulerDecision,
    ) {
        TargetedBugDiagnostics.recordAlarmScheduled(
            occurrenceId = alarm.occurrenceId,
            alarmType = alarmTypeLabel(alarm.eventType),
            triggerAtEpochMs = alarm.triggerAtEpochMillis,
            windowMs = scheduled.windowMs,
            schedulerApi = scheduled.schedulerApi,
            exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(capability),
            reasonFromSync = TargetedBugDiagnostics.NotificationTraceContext.syncReason,
            semanticPlannedAtEpochMs = alarm.plannedAtEpochMillis,
            schedulerDecision = decision.name,
            wallClockMs = System.currentTimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )
        TargetedBugDiagnostics.recordSchedulerDecision(
            decision = decision.name,
            occurrenceId = alarm.occurrenceId,
            eventType = alarm.eventType.name,
            semanticPlannedAtEpochMs = alarm.plannedAtEpochMillis,
            triggerAtEpochMs = alarm.triggerAtEpochMillis,
            schedulerApi = scheduled.schedulerApi,
        )
    }

    private fun cancelSiblingPlannedIdentities(alarm: BoundaryAlarmPlan) {
        if (alarm.eventType != BoundaryEventType.PLANNED_BOUNDARY) {
            return
        }
        val semantic = alarm.plannedAtEpochMillis
        val candidates = listOf(
            PlannedBoundaryTiming.biasedTriggerAt(semantic),
            semantic,
        ).distinct()
        candidates.forEach { trigger ->
            if (trigger == alarm.triggerAtEpochMillis) {
                return@forEach
            }
            val sibling = alarm.copy(triggerAtEpochMillis = trigger)
            pendingIntentFor(
                sibling,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleAlarmClock(triggerAt: Long, operation: PendingIntent): ScheduledApi {
        val showIntent = PendingIntent.getActivity(
            context,
            ALARM_CLOCK_SHOW_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val clockInfo = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
        alarmManager.setAlarmClock(clockInfo, operation)
        return ScheduledApi(schedulerApi = "setAlarmClock", windowMs = 0L)
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private data class ScheduledApi(val schedulerApi: String, val windowMs: Long)

    private fun scheduleCapabilityGatedReminder(
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
            BoundaryEventType.DEFERRED_REMINDER -> "deferred"
        }
    }

    private fun pendingIntentFor(
        alarm: BoundaryAlarmPlan,
        flags: Int,
    ): PendingIntent? {
        // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START - shared canonical Intent
        val intent = PracticeAlarmIntents.buildReceiverIntent(context, alarm)
        // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END
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

        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        private const val ALARM_CLOCK_SHOW_REQUEST_CODE = 0x504C414E // "PLAN"
        // 10.08.2026 Post-release fixes cursor by Me4Hik END

        fun actionFor(alarm: BoundaryAlarmPlan): String = PracticeAlarmIdentity.actionFor(alarm)

        fun requestCodeFor(alarm: BoundaryAlarmPlan): Int = PracticeAlarmIdentity.requestCodeFor(alarm)
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
