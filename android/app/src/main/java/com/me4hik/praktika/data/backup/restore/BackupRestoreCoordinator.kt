// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.slot.BackupSlotCandidate
import com.me4hik.praktika.data.backup.slot.BackupSlotSelector
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRestoreCoordinator(
    private val database: PraktikaDatabase,
    private val restorer: BackupRestoreEngine,
    private val targetValidator: BackupRestoreTargetValidator = BackupRestoreTargetValidator(),
    private val cycleRepository: CycleRepository? = null,
    private val notificationCoordinator: PracticeNotificationCoordinator? = null,
    private val postRestoreVerifier: PostRestoreDataVerifier = NoOpPostRestoreDataVerifier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val reconcileAction: suspend () -> CycleResult = {
        requireNotNull(cycleRepository) { "cycleRepository required for default reconcileAction" }
            .syncEnvironmentAndReconcile()
    },
    private val notificationSyncAction: suspend () -> NotificationSyncObservation = {
        requireNotNull(notificationCoordinator) {
            "notificationCoordinator required for default notificationSyncAction"
        }.sync(NotificationSyncReason.MUTATION)
        NotificationSyncObservation.Invoked
    },
) {
    suspend fun inspectLatest(
        storage: BackupStorageProvider,
    ): BackupRestoreCoordinatorResult {
        val selection = selectLatestValid(storage.inspectSlots())
            ?: return BackupRestoreCoordinatorResult.NoValidBackup

        val preview = BackupRestorePreviewBuilder.fromEnvelope(
            slot = selection.slot,
            envelope = selection.envelope,
        )
        return BackupRestoreCoordinatorResult.PreviewReady(
            preview = preview,
            identity = preview.identity,
        )
    }

    suspend fun checkTargetEligibility(): RestoreTargetEligibility {
        return when (val result = targetValidator.validateFreshEmptyTarget(database)) {
            null -> RestoreTargetEligibility.RestoreAvailable
            BackupRestoreResult.TargetNotEmpty -> RestoreTargetEligibility.TargetNotEmpty
            is BackupRestoreResult.TargetDatabaseUnsafe -> RestoreTargetEligibility.TargetUnsafe(result.reason)
            else -> RestoreTargetEligibility.TargetUnsafe(TargetDatabaseUnsafeReason.MISSING_PRACTICE_STATE)
        }
    }

    suspend fun executeRestore(
        storage: BackupStorageProvider,
        previewIdentity: BackupRestoreSelectedIdentity,
        preview: BackupRestorePreview? = null,
    ): BackupRestoreCoordinatorResult {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A prepare/complete split
        return when (val prepared = prepareExecuteRestore(storage, previewIdentity, preview)) {
            is PrepareExecuteRestoreResult.Ready -> completePreparedRestore(prepared)
            is PrepareExecuteRestoreResult.Terminal -> prepared.result
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A physical prepare vs Room complete
    /**
     * Physical SAF re-inspect + identity check + in-memory envelope materialization.
     * Intended to run under [com.me4hik.praktika.data.backup.write.BackupIoSessionGate].
     */
    suspend fun prepareExecuteRestore(
        storage: BackupStorageProvider,
        previewIdentity: BackupRestoreSelectedIdentity,
        preview: BackupRestorePreview? = null,
    ): PrepareExecuteRestoreResult {
        val selection = selectLatestValid(storage.inspectSlots())
        val currentIdentity = selection?.let {
            BackupRestoreSelectedIdentity.from(it.slot, it.envelope)
        }

        if (currentIdentity == null || !currentIdentity.matches(previewIdentity)) {
            return PrepareExecuteRestoreResult.Terminal(
                BackupRestoreCoordinatorResult.PreviewStale(
                    previousIdentity = previewIdentity,
                    currentIdentity = currentIdentity,
                ),
            )
        }

        val resolvedPreview = preview ?: BackupRestorePreviewBuilder.fromEnvelope(
            slot = selection.slot,
            envelope = selection.envelope,
        )
        return PrepareExecuteRestoreResult.Ready(
            envelope = selection.envelope,
            preview = resolvedPreview,
        )
    }

    /**
     * Room restore + post-restore reconcile. Must run OUTSIDE the shared IO gate.
     * Uses only the already-materialized envelope from [prepareExecuteRestore].
     */
    suspend fun completePreparedRestore(
        prepared: PrepareExecuteRestoreResult.Ready,
    ): BackupRestoreCoordinatorResult {
        return try {
            when (val restoreResult = restorer.restore(prepared.envelope)) {
                BackupRestoreResult.Success -> handleRestoreSuccess(
                    preview = prepared.preview,
                    restoredEnvelope = prepared.envelope,
                )

                else -> BackupRestoreCoordinatorResult.RestoreCoreFailure(restoreResult)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private suspend fun handleRestoreSuccess(
        preview: BackupRestorePreview,
        restoredEnvelope: PraktikaBackupEnvelope,
    ): BackupRestoreCoordinatorResult {
        val verificationOutcome = when (val verification = postRestoreVerifier.verify(restoredEnvelope)) {
            is PostRestoreVerificationResult.Mismatch -> {
                return BackupRestoreCoordinatorResult.RestoreDataSuccessVerificationFailure(
                    preview = preview,
                    verification = verification,
                )
            }

            PostRestoreVerificationResult.NotRequested -> verification
            is PostRestoreVerificationResult.Success -> verification
        }

        // 10.08.2026 Post-release fixes cursor by Me4Hik START - restore coordinator IO dispatch for post-restore runtime sync
        val reconcileResult = try {
            withContext(ioDispatcher) {
                reconcileAction()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            return BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning(
                preview = preview,
                verification = verificationOutcome,
                dataRestoreCommitted = true,
                reconcileFailureClassName = exception.javaClass.name,
                reconcileFailureMessage = exception.message?.take(200),
                notificationSync = null,
            )
        }

        val notificationSync = withContext(ioDispatcher) {
            notificationSyncAction()
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END

        return if (postRestoreVerifier === NoOpPostRestoreDataVerifier) {
            BackupRestoreCoordinatorResult.FullSuccess(
                preview = preview,
                verification = PostRestoreVerificationResult.NotRequested,
                reconcileResult = reconcileResult,
                notificationSync = notificationSync,
            )
        } else {
            BackupRestoreCoordinatorResult.FullSuccess(
                preview = preview,
                verification = verificationOutcome,
                reconcileResult = reconcileResult,
                notificationSync = notificationSync,
            )
        }
    }

    private data class ValidSelection(
        val slot: BackupSlotId,
        val envelope: PraktikaBackupEnvelope,
    )

    private fun selectLatestValid(inspection: SlotInspectionResult): ValidSelection? {
        val candidates = listOf(
            inspection.slotA.toCandidate(BackupSlotId.A),
            inspection.slotB.toCandidate(BackupSlotId.B),
        )
        val slot = BackupSlotSelector.selectBest(candidates) ?: return null
        val result = inspection.resultFor(slot)
        if (result !is SlotReadResult.Valid) {
            return null
        }
        return ValidSelection(slot = slot, envelope = result.envelope)
    }

    private fun SlotReadResult.toCandidate(slotId: BackupSlotId): BackupSlotCandidate {
        val validation = when (this) {
            is SlotReadResult.Valid -> BackupFormatValidationResult.Valid(envelope)
            else -> BackupFormatValidationResult.Invalid(
                BackupFormatFailureReason.InvalidMetadata,
                "not-valid",
            )
        }
        return BackupSlotCandidate(slotId = slotId, validation = validation)
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
