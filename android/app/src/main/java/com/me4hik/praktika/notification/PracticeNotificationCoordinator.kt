// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - coordinator sync
// 10.08.2026 Post-release fixes cursor by Me4Hik START - notification sync diagnostics
package com.me4hik.praktika.notification

import android.util.Log
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.runtime.PraktikaRuntimeInitializer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PracticeNotificationCoordinator(
    private val initializer: PraktikaRuntimeInitializer,
    private val cycleRepository: CycleRepository,
    private val practiceReadRepository: PracticeReadRepository,
    private val permissionRepository: NotificationPermissionPolicy,
    private val soundEnabledProvider: suspend () -> Boolean,
    private val selectedSoundIdProvider: suspend () -> String = {
        com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT
    },
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
            syncLocked(reason)
        }
    }

    /** Must run under [mutex]. */
    private suspend fun syncLocked(reason: NotificationSyncReason) {
        try {
            notificationPresenter.ensureChannelsCreated()
            cycleRepository.syncEnvironmentAndReconcile()
            val snapshot = readSnapshot()
            val permissionRequested = permissionRepository.permissionRequested.first()
            val soundEnabled = soundEnabledProvider()
            val selectedSoundId = selectedSoundIdProvider()
            val permissionState = permissionRepository.evaluateUiState(
                permissionRequested = permissionRequested,
                soundEnabled = soundEnabled,
                selectedSoundId = selectedSoundId,
            )
            val capability = permissionRepository.toDeliveryCapability(permissionState)
            TargetedBugDiagnostics.NotificationTraceContext.syncReason = reason.name
            TargetedBugDiagnostics.NotificationTraceContext.deliveryCapability = capability.name
            val activeNotificationOccurrenceId = readActiveOccurrenceIdBestEffort()
            val activeNotificationKind = readActiveKindBestEffort()
            val activeNotificationChannelId = readActiveChannelIdBestEffort()
            val hostingCustomChannelId = activeNotificationChannelId?.takeIf { channelId ->
                activeNotificationKind == PracticeNotificationKind.QUESTION &&
                    PracticeDueSoundChannelRouter.isCustomDueSoundChannelId(channelId)
            }
            val forceRefreshDueQuestion = reason == NotificationSyncReason.SOUND_CHANGED
            val plan = NotificationPlanner.plan(
                input = NotificationPlanningInput(
                    isPracticeStarted = snapshot.practiceState.isPracticeStarted,
                    isPaused = snapshot.practiceState.isPaused,
                    nowEpochMillis = timeProvider.nowEpochMillis(),
                    currentOccurrence = snapshot.incompleteOccurrence?.toNotificationSnapshot(),
                    notificationCapability = capability,
                    activeNotificationOccurrenceId = activeNotificationOccurrenceId,
                    activeNotificationKind = activeNotificationKind,
                    quietCatchUp = reason.isQuietCatchUp(),
                    forceRefreshDueQuestion = forceRefreshDueQuestion,
                ),
                soundEnabled = soundEnabled,
                selectedSoundId = selectedSoundId,
            )
            // APP_START: reconcile + arm alarms only. Never post/cancel via NotificationManager —
            // nested ensureInitialized must not steal alerting ownership from EXPIRY/PLANNED/DEFERRED.
            val applyNotificationPresentation = reason != NotificationSyncReason.APP_START
            var cancelledActiveNotification = false
            var postedOrReplacedNotification = false
            if (applyNotificationPresentation) {
                if (plan.cancelNotification) {
                    cancelledActiveNotification = cancelPracticeNotificationsBestEffort()
                } else {
                    cancelLegacyPracticeNotificationsBestEffort(
                        currentOccurrenceId = snapshot.incompleteOccurrence?.id,
                        syncReason = reason.name,
                    )
                }
            }
            val previousAlarms = scheduledAlarms
            scheduledAlarms = listOfNotNull(
                plan.plannedBoundaryAlarm,
                plan.expiryBoundaryAlarm,
                plan.deferredReminderAlarm,
            )
            alarmScheduler.scheduleAlarms(plan, previousAlarms)
            val presented = if (applyNotificationPresentation) {
                plan.showNotification?.let { showPlan ->
                    postedOrReplacedNotification = notificationPresenter.showNotification(showPlan)
                    if (postedOrReplacedNotification) showPlan else null
                }
            } else {
                null
            }
            // Keep pre-sync QUESTION hosting custom channel unless this sync actually
            // replaced/posted or cancelled that shade entry (any sync reason).
            val preserveHostingChannel =
                hostingCustomChannelId != null &&
                    !postedOrReplacedNotification &&
                    !cancelledActiveNotification
            notificationPresenter.pruneCustomDueSoundChannels(
                selectedSoundId = selectedSoundId,
                additionalKeepChannelIds = if (preserveHostingChannel) {
                    setOf(hostingCustomChannelId!!)
                } else {
                    emptySet()
                },
            )
            if (DiagnosticsRecorder.isInitialized()) {
                val occurrence = snapshot.incompleteOccurrence
                DiagnosticsRecorder.get().record(
                    category = DiagnosticCategory.NOTIFICATION,
                    name = "notification_sync_result",
                    metadata = mapOf(
                        "reason" to reason.name,
                        "capability" to capability.name,
                        "cancel_notification" to (
                            if (applyNotificationPresentation) {
                                plan.cancelNotification.toString()
                            } else {
                                "false"
                            }
                        ),
                        "show_notification" to (presented != null).toString(),
                        "show_kind" to (presented?.kind?.name ?: ""),
                        "suppress_alert" to (presented?.suppressAlert?.toString() ?: ""),
                        "alarm_count" to scheduledAlarms.size.toString(),
                        "occurrence_id" to (occurrence?.id?.toString() ?: ""),
                        "occurrence_status" to (occurrence?.status?.name ?: ""),
                        "planned_alarm_trigger_at" to (
                            plan.plannedBoundaryAlarm?.triggerAtEpochMillis?.toString() ?: ""
                        ),
                        "expiry_alarm_trigger_at" to (
                            plan.expiryBoundaryAlarm?.triggerAtEpochMillis?.toString() ?: ""
                        ),
                        "deferred_alarm_trigger_at" to (
                            plan.deferredReminderAlarm?.triggerAtEpochMillis?.toString() ?: ""
                        ),
                    ),
                )
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Notification sync failed reason=$reason", exception)
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().recordCaughtException(
                    "PracticeNotificationCoordinator.sync",
                    exception,
                )
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
                    notificationPresenter.cancelCurrentPracticeNotification()
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
                    cancelLegacyPracticeNotificationsBestEffort(
                        currentOccurrenceId = snapshot.incompleteOccurrence?.id,
                        syncReason = "NOTIFICATION_TAP_STALE",
                    )
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

    suspend fun handleNotificationDefer(
        occurrenceId: Long,
        plannedAtEpochMillis: Long,
        durationMinutes: Int,
    ): NotificationDeferDecision {
        if (!initializer.ensureInitialized()) {
            return NotificationDeferDecision.Ignore
        }
        return mutex.withLock {
            try {
                cycleRepository.syncEnvironmentAndReconcile()
                val snapshot = readSnapshot()
                val practiceState = snapshot.practiceState
                if (!practiceState.isPracticeStarted || practiceState.isPaused) {
                    return@withLock NotificationDeferDecision.Ignore
                }
                val target = snapshot.incompleteOccurrence?.takeIf { it.id == occurrenceId }
                    ?: cycleRepository.getOccurrenceById(occurrenceId)
                val occurrence = target ?: return@withLock NotificationDeferDecision.Ignore
                if (occurrence.id != occurrenceId) {
                    return@withLock NotificationDeferDecision.Ignore
                }
                if (occurrence.plannedAtEpochMillis != plannedAtEpochMillis) {
                    return@withLock NotificationDeferDecision.Ignore
                }
                if (snapshot.incompleteOccurrence?.id != occurrence.id) {
                    return@withLock NotificationDeferDecision.Ignore
                }
                if (occurrence.status != QuestionOccurrenceStatus.AVAILABLE) {
                    return@withLock NotificationDeferDecision.Ignore
                }
                val sanitizedMinutes = DeferDurationOptions.sanitize(durationMinutes)
                val result = cycleRepository.deferAvailableOccurrence(
                    expectedOccurrenceId = occurrenceId,
                    durationMinutes = sanitizedMinutes,
                )
                // Re-plan under the same lock: Clock-like snoozed notification on the same id.
                syncLocked(NotificationSyncReason.MUTATION)
                val appliedMinutes = (result as? CycleResult.DeferCompleted)?.durationMinutes
                    ?: sanitizedMinutes
                NotificationDeferDecision.Deferred(appliedMinutes)
            } catch (exception: Exception) {
                Log.e(TAG, "Notification defer handling failed", exception)
                NotificationDeferDecision.Ignore
            }
        }
    }

    private fun readActiveOccurrenceIdBestEffort(): Long? {
        return try {
            notificationPresenter.findActivePracticeNotificationOccurrenceId()
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            Log.w(TAG, "Failed to read active Practice notification", exception)
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().recordCaughtException(
                    "PracticeNotificationCoordinator.activeNotification",
                    exception,
                )
            }
            null
        }
    }

    private fun readActiveKindBestEffort(): PracticeNotificationKind? {
        return try {
            notificationPresenter.findActivePracticeNotificationKind()
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            Log.w(TAG, "Failed to read active Practice notification kind", exception)
            null
        }
    }

    private fun readActiveChannelIdBestEffort(): String? {
        return try {
            notificationPresenter.findActivePracticeNotificationChannelId()
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            Log.w(TAG, "Failed to read active Practice notification channel", exception)
            null
        }
    }

    private fun cancelPracticeNotificationsBestEffort(): Boolean {
        return try {
            notificationPresenter.cancelAllPracticeNotifications()
            true
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            Log.w(TAG, "Failed to cancel Practice notifications", exception)
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().recordCaughtException(
                    "PracticeNotificationCoordinator.cancelAllPracticeNotifications",
                    exception,
                )
            }
            false
        }
    }

    private fun cancelLegacyPracticeNotificationsBestEffort(
        currentOccurrenceId: Long?,
        syncReason: String,
    ) {
        try {
            notificationPresenter.cancelLegacyPracticeNotifications(
                currentOccurrenceId = currentOccurrenceId,
                syncReason = syncReason,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            Log.w(TAG, "Legacy Practice notification cleanup failed", exception)
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().record(
                    category = DiagnosticCategory.NOTIFICATION,
                    name = "PRACTICE_NOTIFICATION_LEGACY_CLEANUP_FAILED",
                    metadata = buildMap {
                        put("sync_reason", syncReason)
                        currentOccurrenceId?.let { put("current_occurrence_id", it.toString()) }
                        put("exception_class", exception.javaClass.simpleName)
                    },
                )
            }
        }
    }

    private suspend fun syncAfterTap() {
        syncLocked(NotificationSyncReason.NOTIFICATION_TAP)
    }

    private suspend fun readSnapshot(): PracticeReadSnapshot {
        return practiceReadRepository.readSnapshot()
    }

    private fun QuestionOccurrenceEntity.toNotificationSnapshot(): NotificationOccurrenceSnapshot {
        return NotificationOccurrenceSnapshot(
            occurrenceId = id,
            status = status,
            plannedAtEpochMillis = plannedAtEpochMillis,
            availableUntilEpochMillis = availableUntilEpochMillis,
            questionTextSnapshot = questionTextSnapshot,
            openedAtEpochMillis = openedAtEpochMillis,
            deferredUntilEpochMillis = deferredUntilEpochMillis,
            zoneId = zoneId,
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
