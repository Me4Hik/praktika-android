// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A backup folder setup coordinator
package com.me4hik.praktika.data.backup.setup

import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.restore.BackupPayloadSemanticComparator
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.BackupAttemptTrigger
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupPhysicalSlotSafetyPolicy
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.backup.write.BackupTreeUriSanitizer
import com.me4hik.praktika.data.backup.write.BackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.CommitAuthorizedFolderResult
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Dormant Stage 6.3A core: candidate setup/replacement force-write, receipt, auth commit,
 * post-switch catch-up, and safe disable+grant release. No Settings UI.
 */
class BackupFolderSetupCoordinator(
    private val writeStateRepository: BackupWriteStateRepository,
    private val ioGate: BackupIoSessionGate,
    private val authorizedBackupService: AuthorizedBackupService,
    private val candidateAccess: SetupCandidateAccess,
    private val exportAction: suspend () -> BackupExportResult,
    private val metadataFactory: BackupSnapshotMetadataFactory,
) {
    private val logicalMutex = Mutex()
    private val tokenSeq = AtomicLong(1L)

    private var session: CandidateSession? = null

    /** Exposed for host identity tests. */
    val sharedIoGateForTests: BackupIoSessionGate
        get() = ioGate

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A3 failed-inspect grant cleanup
    /** Host-test seam: live candidate session presence (never URI). */
    internal fun hasCandidateSessionForTests(): Boolean = session != null
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    suspend fun inspectCandidate(
        uriString: String,
        grantFlags: Int,
    ): SetupInspectResult = logicalMutex.withLock {
        if (session?.physicalBusy == true) {
            return SetupInspectResult.Busy
        }
        abandonUnlocked(releaseGrant = true)
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A3 failed-inspect grant cleanup
        val startingAuthorized = writeStateRepository.snapshot().authorizedTreeUri
        val candidateWasAuthorizedAtAttemptStart =
            BackupTreeUriSanitizer.isUsableTreeUriString(startingAuthorized) &&
                startingAuthorized == uriString
        val result = ioGate.withExclusive {
            var sessionInstalledThisAttempt = false
            try {
                when (val connect = candidateAccess.connectTransient(uriString, grantFlags)) {
                    SetupCandidateConnectResult.PermissionLost -> {
                        cleanupFailedPreSessionCandidateGrant(
                            candidateUriString = uriString,
                            grantFlags = grantFlags,
                            candidateWasAuthorizedAtAttemptStart = candidateWasAuthorizedAtAttemptStart,
                        )
                        return@withExclusive SetupInspectResult.PermissionLost
                    }
                    SetupCandidateConnectResult.Unavailable -> {
                        cleanupFailedPreSessionCandidateGrant(
                            candidateUriString = uriString,
                            grantFlags = grantFlags,
                            candidateWasAuthorizedAtAttemptStart = candidateWasAuthorizedAtAttemptStart,
                        )
                        return@withExclusive SetupInspectResult.Unavailable
                    }
                    SetupCandidateConnectResult.ProviderFailure -> {
                        cleanupFailedPreSessionCandidateGrant(
                            candidateUriString = uriString,
                            grantFlags = grantFlags,
                            candidateWasAuthorizedAtAttemptStart = candidateWasAuthorizedAtAttemptStart,
                        )
                        return@withExclusive SetupInspectResult.ProviderFailure
                    }
                    SetupCandidateConnectResult.Success -> Unit
                }
                val storage = candidateAccess.createStorage(uriString)
                if (storage == null) {
                    cleanupFailedPreSessionCandidateGrant(
                        candidateUriString = uriString,
                        grantFlags = grantFlags,
                        candidateWasAuthorizedAtAttemptStart = candidateWasAuthorizedAtAttemptStart,
                    )
                    return@withExclusive SetupInspectResult.Unavailable
                }
                val inspection = storage.inspectSlots()
                val roomPayload = when (val export = exportAction()) {
                    is BackupExportResult.DatabaseUnsafe -> null to true
                    is BackupExportResult.ReadFailure -> null to false
                    is BackupExportResult.Success -> export.payload to false
                }
                val classification = if (roomPayload.second) {
                    SetupCandidateClassification.UNSAFE_DATABASE
                } else {
                    classifyInspection(inspection, roomPayload.first)
                }
                val token = tokenSeq.getAndIncrement()
                session = CandidateSession(
                    uriString = uriString,
                    grantFlags = grantFlags,
                    storage = storage,
                    inspectionToken = token,
                    classification = classification,
                    receipt = null,
                )
                sessionInstalledThisAttempt = true
                SetupInspectResult.Ready(
                    SetupCandidateInspection(token = token, classification = classification),
                )
            } catch (cancel: CancellationException) {
                if (!sessionInstalledThisAttempt) {
                    withContext(NonCancellable) {
                        cleanupFailedPreSessionCandidateGrant(
                            candidateUriString = uriString,
                            grantFlags = grantFlags,
                            candidateWasAuthorizedAtAttemptStart = candidateWasAuthorizedAtAttemptStart,
                        )
                    }
                }
                throw cancel
            } catch (exception: Exception) {
                if (!sessionInstalledThisAttempt) {
                    cleanupFailedPreSessionCandidateGrant(
                        candidateUriString = uriString,
                        grantFlags = grantFlags,
                        candidateWasAuthorizedAtAttemptStart = candidateWasAuthorizedAtAttemptStart,
                    )
                }
                throw exception
            }
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        return result
    }

    suspend fun verifyWrite(inspectionToken: Long): SetupVerifyWriteResult = logicalMutex.withLock {
        val current = session ?: return SetupVerifyWriteResult.NoSession
        if (current.physicalBusy) {
            return SetupVerifyWriteResult.Busy
        }
        if (current.inspectionToken != inspectionToken) {
            return SetupVerifyWriteResult.NoSession
        }
        current.physicalBusy = true
        try {
            ioGate.withExclusive {
                val storage = current.storage
                val inspection = storage.inspectSlots()
                val export = exportAction()
                val roomPayload = when (export) {
                    is BackupExportResult.DatabaseUnsafe ->
                        return@withExclusive SetupVerifyWriteResult.UnsafeDatabase
                    is BackupExportResult.ReadFailure ->
                        return@withExclusive SetupVerifyWriteResult.ExportFailed
                    is BackupExportResult.Success -> export.payload
                }
                val classification = classifyInspection(inspection, roomPayload)
                val previousClassification = current.classification
                current.classification = classification
                if (classification != previousClassification &&
                    previousClassification.allowsForceWrite() &&
                    !classification.allowsForceWrite()
                ) {
                    val token = tokenSeq.getAndIncrement()
                    current.inspectionToken = token
                    return@withExclusive SetupVerifyWriteResult.CandidateStale(
                        SetupCandidateInspection(token = token, classification = classification),
                    )
                }
                if (!classification.allowsForceWrite()) {
                    val token = tokenSeq.getAndIncrement()
                    current.inspectionToken = token
                    return@withExclusive SetupVerifyWriteResult.Blocked(classification)
                }
                when (
                    val written = SetupForcedBackupWriter.forceWrite(
                        storage = storage,
                        inspection = inspection,
                        exportAction = { BackupExportResult.Success(roomPayload) },
                        metadataFactory = metadataFactory,
                    )
                ) {
                    is SetupForcedBackupWriter.Result.Written -> {
                        val receipt = VerifiedSetupWriteReceipt(
                            candidateUriString = current.uriString,
                            slot = written.parts.slot,
                            sequence = written.parts.sequence,
                            createdAtEpochMillis = written.parts.createdAtEpochMillis,
                            checksumSha256 = written.parts.checksumSha256,
                        )
                        current.receipt = receipt
                        SetupVerifyWriteResult.Verified(receipt)
                    }

                    SetupForcedBackupWriter.Result.BlockedHardRefusal -> {
                        val token = tokenSeq.getAndIncrement()
                        current.inspectionToken = token
                        SetupVerifyWriteResult.Blocked(classification)
                    }

                    SetupForcedBackupWriter.Result.SequenceExhausted ->
                        SetupVerifyWriteResult.Blocked(SetupCandidateClassification.BOTH_UNTRUSTED)

                    SetupForcedBackupWriter.Result.UnsafeDatabase ->
                        SetupVerifyWriteResult.UnsafeDatabase

                    SetupForcedBackupWriter.Result.ExportFailed ->
                        SetupVerifyWriteResult.ExportFailed

                    SetupForcedBackupWriter.Result.WriteFailed ->
                        SetupVerifyWriteResult.WriteFailed
                }
            }
        } finally {
            current.physicalBusy = false
        }
    }

    suspend fun commitVerifiedCandidate(): SetupCommitResult = logicalMutex.withLock {
        val current = session ?: return SetupCommitResult.ReceiptMissing
        if (current.physicalBusy) {
            return SetupCommitResult.Busy
        }
        val receipt = current.receipt ?: return SetupCommitResult.ReceiptMissing
        current.physicalBusy = true
        try {
            val identityOk = ioGate.withExclusive {
                recheckReceiptIdentity(current, receipt)
            }
            if (!identityOk) {
                current.receipt = null
                return SetupCommitResult.ReceiptStale
            }
            val commitResult = try {
                writeStateRepository.commitAuthorizedFolderAfterVerifiedWrite(
                    uriString = receipt.candidateUriString,
                    successfulBackupAtEpochMillis = receipt.createdAtEpochMillis,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                return SetupCommitResult.CommitFailed
            }
            return when (commitResult) {
                CommitAuthorizedFolderResult.InvalidUri -> SetupCommitResult.InvalidUri
                CommitAuthorizedFolderResult.Success -> {
                    var catchupScheduled = false
                    try {
                        authorizedBackupService.requestBackup(
                            reason = BackupRequestReason.MANUAL,
                            trigger = BackupAttemptTrigger.MANUAL,
                        )
                        catchupScheduled = true
                    } catch (_: Exception) {
                        catchupScheduled = false
                    }
                    current.receipt = null
                    current.authorized = true
                    session = null
                    SetupCommitResult.Success(
                        verifiedCreatedAtEpochMillis = receipt.createdAtEpochMillis,
                        postSwitchCatchupScheduled = catchupScheduled,
                    )
                }
            }
        } finally {
            current.physicalBusy = false
        }
    }

    suspend fun abandonSession(): SetupAbandonResult = logicalMutex.withLock {
        if (session?.physicalBusy == true) {
            return SetupAbandonResult.Busy
        }
        abandonUnlocked(releaseGrant = true)
        SetupAbandonResult.Success
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 abandon-when-idle
    /**
     * Deterministic abandon for runtime-owned cleanup: waits on [logicalMutex] until any
     * in-flight setup op finishes, then clears unbound session/grant. Never returns Busy.
     * Lock order: logicalMutex → [BackupIoSessionGate] (via [abandonUnlocked]).
     */
    suspend fun abandonSessionWhenIdle(): SetupAbandonResult = logicalMutex.withLock {
        abandonUnlocked(releaseGrant = true)
        SetupAbandonResult.Success
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    suspend fun disableAutomaticBackupAndReleasePermission(): DisableAutomaticBackupResult =
        logicalMutex.withLock {
            if (session?.physicalBusy == true) {
                return DisableAutomaticBackupResult.Busy
            }
            // Setup candidate session should not race disable; abandon unbound candidate first.
            abandonUnlocked(releaseGrant = true)

            val priorAuth = writeStateRepository.snapshot().authorizedTreeUri
            try {
                writeStateRepository.disableAutomaticBackup()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                return DisableAutomaticBackupResult.AuthClearFailed
            }

            if (priorAuth.isNullOrBlank()) {
                return DisableAutomaticBackupResult.Success
            }

            var releaseFailed = false
            ioGate.withExclusive {
                try {
                    candidateAccess.releasePersistedGrant(
                        uriString = priorAuth,
                        grantFlags = DEFAULT_RW_FLAGS,
                    )
                } catch (_: Exception) {
                    releaseFailed = true
                }
            }
            return if (releaseFailed) {
                DisableAutomaticBackupResult.DisabledWithPermissionCleanupWarning
            } else {
                DisableAutomaticBackupResult.Success
            }
        }

    private suspend fun abandonUnlocked(releaseGrant: Boolean) {
        val current = session ?: return
        session = null
        if (!releaseGrant || current.authorized) {
            return
        }
        val auth = writeStateRepository.snapshot().authorizedTreeUri
        if (auth != null && auth == current.uriString) {
            return
        }
        ioGate.withExclusive {
            try {
                candidateAccess.releasePersistedGrant(current.uriString, current.grantFlags)
            } catch (_: Exception) {
                // best-effort unbound cleanup
            }
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A3 failed-inspect grant cleanup
    /**
     * Pre-session only. Caller must already hold [logicalMutex] and [ioGate] exclusive.
     * Must NOT acquire [ioGate] again (gate is not reentrant).
     *
     * KEEP if current auth == candidate, or candidate was authorized at attempt start and
     * current auth is still some non-null URI (replacement preserves old grant).
     * Otherwise RELEASE (includes auth null / disable least-privilege).
     */
    private suspend fun cleanupFailedPreSessionCandidateGrant(
        candidateUriString: String,
        grantFlags: Int,
        candidateWasAuthorizedAtAttemptStart: Boolean,
    ) {
        val currentAuth = writeStateRepository.snapshot().authorizedTreeUri
        val currentUsable = BackupTreeUriSanitizer.normalizeOrNull(currentAuth)
        val keep = when {
            currentUsable != null && currentUsable == candidateUriString -> true
            candidateWasAuthorizedAtAttemptStart && currentUsable != null -> true
            else -> false
        }
        if (keep) {
            return
        }
        try {
            candidateAccess.releasePersistedGrant(candidateUriString, grantFlags)
        } catch (_: Exception) {
            // best-effort; original inspect failure category is preserved by caller
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private suspend fun recheckReceiptIdentity(
        current: CandidateSession,
        receipt: VerifiedSetupWriteReceipt,
    ): Boolean {
        if (receipt.candidateUriString != current.uriString) {
            return false
        }
        val inspection = current.storage.inspectSlots()
        val slotResult = when (receipt.slot) {
            BackupSlotId.A -> inspection.slotA
            BackupSlotId.B -> inspection.slotB
        }
        val valid = slotResult as? SlotReadResult.Valid ?: return false
        val envelope = valid.envelope
        return receipt.matchesPhysical(
            candidateUriString = current.uriString,
            slot = receipt.slot,
            sequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            checksumSha256 = envelope.backupChecksumSha256,
        )
    }

    private fun classifyInspection(
        inspection: SlotInspectionResult,
        roomPayload: PraktikaBackupPayload?,
    ): SetupCandidateClassification {
        BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection)?.let { refusal ->
            return when (refusal) {
                is com.me4hik.praktika.data.backup.write.BackupOutcome.AmbiguousSlot ->
                    SetupCandidateClassification.AMBIGUOUS
                is com.me4hik.praktika.data.backup.write.BackupOutcome.BothSlotsInvalid,
                is com.me4hik.praktika.data.backup.write.BackupOutcome.UntrustedArtifactsPresent,
                -> SetupCandidateClassification.BOTH_UNTRUSTED
                is com.me4hik.praktika.data.backup.write.BackupOutcome.StorageReadFailed ->
                    SetupCandidateClassification.UNREADABLE
                else -> SetupCandidateClassification.BOTH_UNTRUSTED
            }
        }
        if (BackupPhysicalSlotSafetyPolicy.hasValidAndUnreadable(inspection)) {
            return SetupCandidateClassification.VALID_PLUS_UNREADABLE
        }
        val a = inspection.slotA
        val b = inspection.slotB
        if (a is SlotReadResult.Missing && b is SlotReadResult.Missing) {
            return SetupCandidateClassification.EMPTY
        }
        if (isRecoverableUntrusted(a) && b is SlotReadResult.Missing) {
            return SetupCandidateClassification.INVALID_PLUS_MISSING
        }
        if (a is SlotReadResult.Missing && isRecoverableUntrusted(b)) {
            return SetupCandidateClassification.INVALID_PLUS_MISSING
        }
        val latest = BackupPhysicalSlotSafetyPolicy.selectLatestValid(inspection)
        if (latest != null && roomPayload != null) {
            return if (
                BackupPayloadSemanticComparator.equalsSemantically(
                    roomPayload,
                    latest.second.payload,
                )
            ) {
                SetupCandidateClassification.VALID_EQUAL
            } else {
                SetupCandidateClassification.VALID_DIFFERENT
            }
        }
        if (hasUnreadable(a) || hasUnreadable(b)) {
            return SetupCandidateClassification.UNREADABLE
        }
        return SetupCandidateClassification.BOTH_UNTRUSTED
    }

    private fun SetupCandidateClassification.allowsForceWrite(): Boolean {
        return when (this) {
            SetupCandidateClassification.EMPTY,
            SetupCandidateClassification.VALID_EQUAL,
            SetupCandidateClassification.VALID_DIFFERENT,
            SetupCandidateClassification.INVALID_PLUS_MISSING,
            -> true

            else -> false
        }
    }

    private fun isRecoverableUntrusted(result: SlotReadResult): Boolean {
        return result is SlotReadResult.Invalid || result is SlotReadResult.TooLarge
    }

    private fun hasUnreadable(result: SlotReadResult): Boolean {
        return result is SlotReadResult.Unreadable
    }

    private class CandidateSession(
        val uriString: String,
        val grantFlags: Int,
        val storage: BackupStorageProvider,
        var inspectionToken: Long,
        var classification: SetupCandidateClassification,
        var receipt: VerifiedSetupWriteReceipt?,
        var physicalBusy: Boolean = false,
        var authorized: Boolean = false,
    )

    companion object {
        const val DEFAULT_RW_FLAGS: Int =
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
