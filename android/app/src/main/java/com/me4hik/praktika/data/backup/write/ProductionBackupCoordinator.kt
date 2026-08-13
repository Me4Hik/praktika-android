// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.0 production backup coordinator
package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.restore.BackupPayloadSemanticComparator
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProductionBackupCoordinator(
    private val storage: BackupStorageProvider,
    private val exportAction: suspend () -> BackupExportResult,
    private val metadataFactory: BackupSnapshotMetadataFactory,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val writerMutex = Mutex()
    private val pendingRerunLock = Any()

    @Volatile
    private var pendingRerun: Boolean = false

    @Volatile
    private var requestLoopActive: Boolean = false

    fun requestBackup(reason: BackupRequestReason) {
        synchronized(pendingRerunLock) {
            pendingRerun = true
            if (requestLoopActive) {
                return
            }
            requestLoopActive = true
        }
        scope.launch(ioDispatcher) {
            try {
                backupNow(reason)
            } finally {
                synchronized(pendingRerunLock) {
                    requestLoopActive = false
                    if (pendingRerun) {
                        requestBackup(reason)
                    }
                }
            }
        }
    }

    suspend fun backupNow(reason: BackupRequestReason): BackupOutcome {
        return writerMutex.withLock {
            executeCoalescedBackup(reason)
        }
    }

    private suspend fun executeCoalescedBackup(reason: BackupRequestReason): BackupOutcome {
        var lastOutcome: BackupOutcome? = null
        do {
            synchronized(pendingRerunLock) {
                pendingRerun = false
            }
            lastOutcome = executeSinglePass(reason)
        } while (synchronized(pendingRerunLock) { pendingRerun })
        return checkNotNull(lastOutcome)
    }

    private suspend fun executeSinglePass(reason: BackupRequestReason): BackupOutcome {
        reason // diagnostics/tests only; must not alter write policy
        return try {
            val inspection = storage.inspectSlots()

            BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection)?.let { return it }

            val latestValid = BackupPhysicalSlotSafetyPolicy.selectLatestValid(inspection)

            when (val export = exportAction()) {
                is BackupExportResult.DatabaseUnsafe -> BackupOutcome.UnsafeDatabaseState
                is BackupExportResult.ReadFailure ->
                    BackupOutcome.WriteFailed(BackupWriteFailureCategory.EXPORT_READ_FAILURE)

                is BackupExportResult.Success -> {
                    val roomPayload = export.payload

                    if (latestValid != null) {
                        if (
                            BackupPayloadSemanticComparator.equalsSemantically(
                                roomPayload,
                                latestValid.second.payload,
                            )
                        ) {
                            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A NoChange trusted timestamp
                            return BackupOutcome.NoChange(
                                latestValidBackupCreatedAtEpochMillis =
                                    latestValid.second.createdAtEpochMillis,
                            )
                            // 10.08.2026 Post-release fixes cursor by Me4Hik END
                        }
                        if (BackupPhysicalSlotSafetyPolicy.hasValidAndUnreadable(inspection)) {
                            return BackupOutcome.StorageAccessUnreliable
                        }
                    }

                    when (val writePlan = BackupPhysicalSlotSafetyPolicy.planWriteAfterSemanticDiff(inspection)) {
                        is BackupPhysicalSlotSafetyPolicy.WritePlan.SequenceExhausted ->
                            BackupOutcome.SequenceExhausted

                        is BackupPhysicalSlotSafetyPolicy.WritePlan.Ready ->
                            writeVerifiedBackup(
                                payload = roomPayload,
                                targetSlot = writePlan.targetSlot,
                                nextSequence = writePlan.nextSequence,
                            )
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        }
    }

    private suspend fun writeVerifiedBackup(
        payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload,
        targetSlot: com.me4hik.praktika.data.backup.model.BackupSlotId,
        nextSequence: Long,
    ): BackupOutcome {
        val metadata = metadataFactory.capture()
        return when (
            val assembled = BackupEnvelopeAssembler.assemble(
                payload = payload,
                backupSequence = nextSequence,
                metadata = metadata,
            )
        ) {
            is BackupEnvelopeAssemblyResult.Success -> {
                val bytes = BackupJsonEncoder.encodeToUtf8Bytes(assembled.envelope)
                when (
                    val writeResult = storage.writeSlot(
                        slot = targetSlot,
                        bytes = bytes,
                        expectedEnvelope = assembled.envelope,
                    )
                ) {
                    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A Written trusted timestamp
                    is BackupSlotWriteResult.Success ->
                        BackupOutcome.Written(
                            slot = targetSlot,
                            sequence = writeResult.sequence,
                            createdAtEpochMillis = assembled.envelope.createdAtEpochMillis,
                        )
                    // 10.08.2026 Post-release fixes cursor by Me4Hik END

                    else -> mapWriteFailure(writeResult)
                }
            }

            is BackupEnvelopeAssemblyResult.IntegrityFailure,
            is BackupEnvelopeAssemblyResult.InternalValidationFailure,
            BackupEnvelopeAssemblyResult.InvalidAppMetadata,
            BackupEnvelopeAssemblyResult.InvalidSeedVersion,
            BackupEnvelopeAssemblyResult.InvalidSequence,
            BackupEnvelopeAssemblyResult.InvalidTimestamp,
            -> BackupOutcome.WriteFailed(BackupWriteFailureCategory.ENVELOPE_ASSEMBLY_FAILED)
        }
    }

    private fun mapWriteFailure(result: BackupSlotWriteResult): BackupOutcome.WriteFailed {
        val category = when (result) {
            BackupSlotWriteResult.TooLarge,
            BackupSlotWriteResult.Unavailable,
            -> BackupWriteFailureCategory.STORAGE_UNAVAILABLE

            BackupSlotWriteResult.PermissionLost -> BackupWriteFailureCategory.PERMISSION_LOST
            is BackupSlotWriteResult.SlotAmbiguous -> BackupWriteFailureCategory.SLOT_AMBIGUOUS
            is BackupSlotWriteResult.DeleteFailed -> BackupWriteFailureCategory.DELETE_FAILED
            is BackupSlotWriteResult.CreateFailed -> BackupWriteFailureCategory.CREATE_FAILED
            is BackupSlotWriteResult.NameMismatch -> BackupWriteFailureCategory.NAME_MISMATCH
            is BackupSlotWriteResult.OpenFailed -> BackupWriteFailureCategory.WRITE_FAILED
            is BackupSlotWriteResult.WriteFailed -> BackupWriteFailureCategory.WRITE_FAILED
            is BackupSlotWriteResult.PostWriteReadFailed -> BackupWriteFailureCategory.POST_WRITE_READ_FAILED
            is BackupSlotWriteResult.PostWriteValidationFailed ->
                BackupWriteFailureCategory.POST_WRITE_VALIDATION_FAILED

            is BackupSlotWriteResult.ExpectedIdentityMismatch ->
                BackupWriteFailureCategory.EXPECTED_IDENTITY_MISMATCH

            is BackupSlotWriteResult.ProviderFailure -> BackupWriteFailureCategory.PROVIDER_FAILURE
            is BackupSlotWriteResult.Success -> BackupWriteFailureCategory.WRITE_FAILED
        }
        return BackupOutcome.WriteFailed(category)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
