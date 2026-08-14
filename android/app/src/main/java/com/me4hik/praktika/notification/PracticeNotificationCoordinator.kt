// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - coordinator sync
// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification sync diagnostics
package com.me4hik.praktika.notification

import android.util.Log
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.runtime.PraktikaRuntimeInitializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PracticeNotificationCoordinator(
    private val initializer: PraktikaRuntimeInitializer,
    private val cycleRepository: CycleRepository,
    private val practiceReadRepository: PracticeReadRepository,
    private val permissionRepository: NotificationPermissionPolicy,
    private val soundEnabledProvider: suspend () -> Boolean,
    private val alarmScheduler: PlatformAlarmScheduler,
    private val notificationPresenter: PracticeNotificationPresenter,
    private val openRequestStore: NotificationOpenRequestStore,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()
    private var scheduledAlarms: List<BoundaryAlarmPlan> = emptyList()

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    suspend fun handleResidualPlannedRecovery(
        recoveryPlan: BoundaryAlarmPlan,
        useAlarmClock: Boolean,
    ): ResidualPlannedRecoveryResult {
        return mutex.withLock {
            alarmScheduler.scheduleResidualPlannedRecovery(
                recoveryPlan = recoveryPlan,
                useAlarmClock = useAlarmClock,
            )
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    suspend fun sync(reason: NotificationSyncReason) {
        if (DiagnosticsRecorder.isInitialized()) {
            DiagnosticsRecorder.get().record(
                category = DiagnosticCategory.NOTIFICATION,
                name = "notification_sync_started",
                metadata = mapOf("reason" to reason.name),
            )
        }
        if (!initializer.ensureInitialized()) {
            Log.w(TAG, "Initialization failed during sync reason=$reason")
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().record(
                    category = DiagnosticCategory.NOTIFICATION,
                    name = "notification_sync_error",
                    metadata = mapOf(
                        "reason" to reason.name,
                        "error" to "init_failed",
                    ),
                )
            }
            return
        }
        mutex.withLock {
            try {
                notificationPresenter.ensureChannelsCreated()
                cycleRepository.syncEnvironmentAndReconcile()
                val snapshot = readSnapshot()
                val permissionRequested = permissionRepository.permissionRequested.first()
                val permissionState = permissionRepository.evaluateUiState(
                    permissionRequested = permissionRequested,
                    soundEnabled = soundEnabledProvider(),
                )
                val capability = permissionRepository.toDeliveryCapability(permissionState)
                TargetedBugDiagnostics.NotificationTraceContext.syncReason = reason.name
                TargetedBugDiagnostics.NotificationTraceContext.deliveryCapability = capability.name
                val activeNotificationOccurrenceId =
                    notificationPresenter.findActivePracticeNotificationOccurrenceId()
                val soundEnabled = soundEnabledProvider()
                val plan = NotificationPlanner.plan(
                    input = NotificationPlanningInput(
                        isPracticeStarted = snapshot.practiceState.isPracticeStarted,
                        isPaused = snapshot.practiceState.isPaused,
                        nowEpochMillis = timeProvider.nowEpochMillis(),
                        currentOccurrence = snapshot.incompleteOccurrence?.toNotificationSnapshot(),
                        notificationCapability = capability,
                        activeNotificationOccurrenceId = activeNotificationOccurrenceId,
                    ),
                    soundEnabled = soundEnabled,
                )
                if (plan.cancelNotification) {
                    notificationPresenter.cancelAllPracticeNotifications()
                }
                val previousAlarms = scheduledAlarms
                scheduledAlarms = listOfNotNull(plan.plannedBoundaryAlarm, plan.expiryBoundaryAlarm)
                alarmScheduler.scheduleAlarms(plan, previousAlarms)
                plan.showNotification?.let { showPlan ->
                    notificationPresenter.showNotification(showPlan)
                }
                if (DiagnosticsRecorder.isInitialized()) {
                    val occurrence = snapshot.incompleteOccurrence
                    DiagnosticsRecorder.get().record(
                        category = DiagnosticCategory.NOTIFICATION,
                        name = "notification_sync_result",
                        metadata = mapOf(
                            "reason" to reason.name,
                            "capability" to capability.name,
                            "cancel_notification" to plan.cancelNotification.toString(),
                            "show_notification" to (plan.showNotification != null).toString(),
                            "alarm_count" to scheduledAlarms.size.toString(),
                            "occurrence_id" to (occurrence?.id?.toString() ?: ""),
                            "occurrence_status" to (occurrence?.status?.name ?: ""),
                            "planned_alarm_trigger_at" to (
                                plan.plannedBoundaryAlarm?.triggerAtEpochMillis?.toString() ?: ""
                            ),
                            "expiry_alarm_trigger_at" to (
                                plan.expiryBoundaryAlarm?.triggerAtEpochMillis?.toString() ?: ""
                            ),
                        ),
                    )
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Notification sync failed reason=$reason", exception)
                if (DiagnosticsRecorder.isInitialized()) {
                    DiagnosticsRecorder.get().recordCaughtException("PracticeNotificationCoordinator.sync", exception)
                    DiagnosticsRecorder.get().record(
                        category = DiagnosticCategory.NOTIFICATION,
                        name = "notification_sync_error",
                        metadata = mapOf(
                            "reason" to reason.name,
                            "error" to exception.javaClass.simpleName,
                        ),
                    )
                }
            }
        }
    }

    suspend fun handleNotificationTap(
        occurrenceId: Long,
        plannedAtEpochMillis: Long,
        tapSource: NotificationTapSource = NotificationTapSource.DIRECT_TEST,
    ): NotificationTapDecision {
        if (!initializer.ensureInitialized()) {
            return NotificationTapDecision.Ignore
        }
        return mutex.withLock {
            try {
                cycleRepository.syncEnvironmentAndReconcile()
                val snapshot = readSnapshot()
                val target = snapshot.incompleteOccurrence?.takeIf { it.id == occurrenceId }
                    ?: cycleRepository.getOccurrenceById(occurrenceId)
                val openedAtBefore = target?.openedAtEpochMillis
                val decision = NotificationTapPolicy.evaluate(
                    practiceState = snapshot.practiceState,
                    currentIncomplete = snapshot.incompleteOccurrence,
                    targetOccurrence = target,
                    expectedOccurrenceId = occurrenceId,
                    expectedPlannedAtEpochMillis = plannedAtEpochMillis,
                )
                // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - tap validation diagnostic log
                Log.i(
                    NOTIFICATION_TAP_TAG,
                    "evaluate source=$tapSource occurrenceId=$occurrenceId plannedAt=$plannedAtEpochMillis " +
                        "validationResult=$decision openedAtBefore=$openedAtBefore",
                )
                // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
                if (decision is NotificationTapDecision.Open) {
                    cycleRepository.markOccurrenceOpened(decision.occurrenceId)
                    notificationPresenter.cancelPracticeNotification(decision.occurrenceId)
                    openRequestStore.publish(NotificationOpenRequest(decision.occurrenceId))
                    val openedAtAfter = cycleRepository.getOccurrenceById(decision.occurrenceId)
                        ?.openedAtEpochMillis
                    // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - navigation publish diagnostic log
                    Log.i(
                        NOTIFICATION_TAP_TAG,
                        "navigationResult=publish source=$tapSource occurrenceId=${decision.occurrenceId} " +
                            "openedAtAfter=$openedAtAfter",
                    )
                    // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
                } else {
                    notificationPresenter.cancelPracticeNotification(occurrenceId)
                    // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - stale/ignored tap log
                    Log.i(
                        NOTIFICATION_TAP_TAG,
                        "navigationResult=ignore source=$tapSource occurrenceId=$occurrenceId",
                    )
                    // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
                }
                syncAfterTap()
                decision
            } catch (exception: Exception) {
                Log.e(TAG, "Notification tap handling failed", exception)
                NotificationTapDecision.Ignore
            }
        }
    }

    private suspend fun syncAfterTap() {
        val snapshot = readSnapshot()
        val permissionRequested = permissionRepository.permissionRequested.first()
        val permissionState = permissionRepository.evaluateUiState(
            permissionRequested = permissionRequested,
            soundEnabled = soundEnabledProvider(),
        )
        val plan = NotificationPlanner.plan(
            input = NotificationPlanningInput(
                isPracticeStarted = snapshot.practiceState.isPracticeStarted,
                isPaused = snapshot.practiceState.isPaused,
                nowEpochMillis = timeProvider.nowEpochMillis(),
                currentOccurrence = snapshot.incompleteOccurrence?.toNotificationSnapshot(),
                notificationCapability = permissionRepository.toDeliveryCapability(permissionState),
                activeNotificationOccurrenceId =
                    notificationPresenter.findActivePracticeNotificationOccurrenceId(),
            ),
            soundEnabled = soundEnabledProvider(),
        )
        alarmScheduler.scheduleAlarms(plan, scheduledAlarms)
        scheduledAlarms = listOfNotNull(plan.plannedBoundaryAlarm, plan.expiryBoundaryAlarm)
    }

    private suspend fun readSnapshot(): PracticeReadSnapshot {
        return practiceReadRepository.observeSnapshot().first()
    }

    private fun QuestionOccurrenceEntity.toNotificationSnapshot(): NotificationOccurrenceSnapshot {
        return NotificationOccurrenceSnapshot(
            occurrenceId = id,
            status = status,
            plannedAtEpochMillis = plannedAtEpochMillis,
            availableUntilEpochMillis = availableUntilEpochMillis,
            questionTextSnapshot = questionTextSnapshot,
            openedAtEpochMillis = openedAtEpochMillis,
        )
    }

    companion object {
        private const val TAG = "PracticeNotification"
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik START - shared tap diagnostic tag
        const val NOTIFICATION_TAP_TAG = "NotificationTap"
        // 06.08.2026 Stage 12 Notification Tap Proof cursor by Me4Hik END
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
