// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - alarm receiver
package com.me4hik.praktika.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PracticeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, INVALID_ID)
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)
        val triggerAt = intent.getLongExtra(EXTRA_BOUNDARY_EPOCH_MILLIS, INVALID_ID)
        val semanticPlannedAt = intent.getLongExtra(EXTRA_PLANNED_AT_EPOCH_MILLIS, INVALID_ID)
        val receivedAtEpochMs = System.currentTimeMillis()
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        TargetedBugDiagnostics.recordAlarmFired(
            occurrenceId = occurrenceId,
            alarmType = eventType?.lowercase()?.substringBefore("_boundary"),
            receivedAtEpochMs = receivedAtEpochMs,
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
            receiverEventType = eventType,
            semanticPlannedAtEpochMs = semanticPlannedAt.takeIf { it > 0L },
            triggerAtEpochMs = triggerAt.takeIf { it > 0L },
            intentFlags = intent.flags,
            wallClockMs = receivedAtEpochMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            deltaToPlannedMs = if (semanticPlannedAt > 0L) {
                receivedAtEpochMs - semanticPlannedAt
            } else {
                null
            },
            logicalPiIdentity = intent.action,
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
                handleAlarm(context, intent, occurrenceId, eventType, triggerAt, semanticPlannedAt, receivedAtEpochMs)
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
            } catch (exception: Exception) {
                Log.e(TAG, "Alarm receiver failed", exception)
                // 03.09.2026 Case2 minimal observability cursor by Me4Hik START - durable receiver failure
                TargetedBugDiagnostics.recordPlannedAlarmReceiverFailed(
                    occurrenceId = occurrenceId,
                    receiverEventType = eventType,
                    exceptionClass = exception.javaClass.name,
                    wallClockMs = System.currentTimeMillis(),
                    elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    semanticPlannedAtEpochMs = semanticPlannedAt.takeIf { it > 0L },
                    triggerAtEpochMs = triggerAt.takeIf { it > 0L },
                )
                // 03.09.2026 Case2 minimal observability cursor by Me4Hik END
            } finally {
                pendingResult.finish()
            }
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    private suspend fun handleAlarm(
        context: Context,
        intent: Intent,
        occurrenceId: Long,
        eventType: String?,
        triggerAt: Long,
        semanticPlannedAt: Long,
        receivedAtEpochMs: Long,
    ) {
        val runtime = PraktikaRuntimeHolder.get(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val exactCapability = ExactAlarmCapabilityResolver.resolve(alarmManager)
        val powerSnapshot = readPowerDiagnostics(context)

        val intentSnapshot = PlannedBoundaryIntentSnapshot(
            eventType = eventType,
            occurrenceId = occurrenceId,
            semanticPlannedAtEpochMillis = semanticPlannedAt,
            triggerAtEpochMillis = triggerAt,
            intentAction = intent.action,
        )

        if (eventType == BoundaryEventType.EXPIRY_BOUNDARY.name) {
            TargetedBugDiagnostics.recordSchedulerDecision(
                decision = PlannedBoundarySchedulerDecision.NORMAL_REARM.name,
                occurrenceId = occurrenceId,
                eventType = eventType,
                semanticPlannedAtEpochMs = semanticPlannedAt,
                triggerAtEpochMs = triggerAt,
                exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(exactCapability),
                powerSnapshot = powerSnapshot,
            )
            runtime.notificationCoordinator.sync(NotificationSyncReason.EXPIRY_ALARM)
            return
        }

        if (eventType == BoundaryEventType.DEFERRED_REMINDER.name) {
            // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START - entry log for harness proof
            Log.i(TAG, "DEFERRED_REMINDER → sync DEFERRED_ALARM occurrenceId=$occurrenceId")
            // 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END
            runtime.notificationCoordinator.sync(NotificationSyncReason.DEFERRED_ALARM)
            return
        }

        if (eventType != BoundaryEventType.PLANNED_BOUNDARY.name) {
            TargetedBugDiagnostics.recordMalformedAlarmIntent(
                occurrenceId = occurrenceId,
                eventType = eventType,
                triggerAtEpochMs = triggerAt,
                semanticPlannedAtEpochMs = semanticPlannedAt,
            )
            TargetedBugDiagnostics.recordSchedulerDecision(
                decision = PlannedBoundarySchedulerDecision.IGNORE_MALFORMED.name,
                occurrenceId = occurrenceId,
                eventType = eventType,
                semanticPlannedAtEpochMs = semanticPlannedAt,
                triggerAtEpochMs = triggerAt,
                exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(exactCapability),
                powerSnapshot = powerSnapshot,
            )
            runtime.notificationCoordinator.sync(NotificationSyncReason.FOREGROUND)
            return
        }

        if (!runtime.initializer.ensureInitialized()) {
            Log.w(TAG, "Initialization failed during planned alarm")
            // 03.09.2026 Case2 minimal observability cursor by Me4Hik START - durable init failure
            TargetedBugDiagnostics.recordPlannedAlarmInitFailed(
                occurrenceId = occurrenceId,
                receiverEventType = eventType,
                semanticPlannedAtEpochMs = semanticPlannedAt,
                triggerAtEpochMs = triggerAt,
                wallClockMs = System.currentTimeMillis(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
            // 03.09.2026 Case2 minimal observability cursor by Me4Hik END
            return
        }

        runtime.cycleRepository.syncEnvironmentAndReconcile()
        val authoritativeEntity = if (occurrenceId > 0L) {
            runtime.cycleRepository.getOccurrenceById(occurrenceId)
        } else {
            null
        }
        val authoritative = authoritativeEntity?.let {
            AuthoritativeOccurrenceSnapshot(
                occurrenceId = it.id,
                status = it.status,
                plannedAtEpochMillis = it.plannedAtEpochMillis,
            )
        }

        val disposition = PlannedBoundaryReceiverPolicy.parseAndDecide(
            intent = intentSnapshot,
            authoritative = authoritative,
            nowEpochMillis = receivedAtEpochMs,
            exactCapability = exactCapability,
        )

        when (disposition) {
            is PlannedBoundaryReceiverDisposition.Malformed -> {
                TargetedBugDiagnostics.recordMalformedAlarmIntent(
                    occurrenceId = occurrenceId,
                    eventType = eventType,
                    triggerAtEpochMs = triggerAt,
                    semanticPlannedAtEpochMs = semanticPlannedAt,
                )
                TargetedBugDiagnostics.recordSchedulerDecision(
                    decision = disposition.decision.name,
                    occurrenceId = occurrenceId,
                    eventType = eventType,
                    semanticPlannedAtEpochMs = semanticPlannedAt,
                    triggerAtEpochMs = triggerAt,
                    exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(exactCapability),
                    powerSnapshot = powerSnapshot,
                )
                runtime.notificationCoordinator.sync(NotificationSyncReason.FOREGROUND)
            }
            is PlannedBoundaryReceiverDisposition.Sync -> {
                TargetedBugDiagnostics.recordSchedulerDecision(
                    decision = disposition.decision.name,
                    occurrenceId = occurrenceId,
                    eventType = eventType,
                    semanticPlannedAtEpochMs = semanticPlannedAt,
                    triggerAtEpochMs = triggerAt,
                    exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(exactCapability),
                    powerSnapshot = powerSnapshot,
                )
                runtime.notificationCoordinator.sync(disposition.reason)
            }
            is PlannedBoundaryReceiverDisposition.ResidualRecovery -> {
                TargetedBugDiagnostics.recordSchedulerDecision(
                    decision = disposition.decision.name,
                    occurrenceId = occurrenceId,
                    eventType = eventType,
                    semanticPlannedAtEpochMs = semanticPlannedAt,
                    triggerAtEpochMs = triggerAt,
                    exactAlarmCapability = ExactAlarmCapabilityResolver.diagnosticLabel(exactCapability),
                    powerSnapshot = powerSnapshot,
                )
                // Narrow recovery only — do not run full cancel/replan AWI cascade.
                runtime.notificationCoordinator.handleResidualPlannedRecovery(
                    recoveryPlan = disposition.recoveryPlan,
                    useAlarmClock = disposition.useAlarmClock,
                )
                val statusAfter = authoritativeEntity?.status?.name
                    ?: QuestionOccurrenceStatus.SCHEDULED.name
                Log.i(
                    TAG,
                    "residualRecovery decision=${disposition.decision} occurrenceId=$occurrenceId " +
                        "statusAfter=$statusAfter useAlarmClock=${disposition.useAlarmClock}",
                )
            }
        }
    }

    private fun readPowerDiagnostics(context: Context): TargetedBugDiagnostics.PowerDiagnosticsSnapshot {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return TargetedBugDiagnostics.PowerDiagnosticsSnapshot()
        val deviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            null
        }
        val powerSave = powerManager.isPowerSaveMode
        val ignoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            null
        }
        return TargetedBugDiagnostics.PowerDiagnosticsSnapshot(
            deviceIdleMode = deviceIdle,
            powerSaveMode = powerSave,
            ignoringBatteryOptimizations = ignoringBattery,
        )
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    companion object {
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_BOUNDARY_EPOCH_MILLIS = "boundary_epoch_millis"
        const val EXTRA_PLANNED_AT_EPOCH_MILLIS = "planned_at_epoch_millis"
        const val TAG = "PracticeAlarmReceiver"
        private const val INVALID_ID = -1L
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
