// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup force-write helper
package com.me4hik.praktika.data.backup.setup

import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.write.BackupPhysicalSlotSafetyPolicy
import kotlin.coroutines.cancellation.CancellationException

/**
 * Setup-only forced A/B write: bypasses semantic NoChange early-exit only.
 * Reuses physical slot safety, sequence, assembler, checksum, and verified writeSlot.
 */
internal object SetupForcedBackupWriter {
    sealed interface Result {
        data class Written(val parts: ReceiptParts) : Result

        data object BlockedHardRefusal : Result

        data object SequenceExhausted : Result

        data object UnsafeDatabase : Result

        data object ExportFailed : Result

        data object WriteFailed : Result
    }

    data class ReceiptParts(
        val slot: com.me4hik.praktika.data.backup.model.BackupSlotId,
        val sequence: Long,
        val createdAtEpochMillis: Long,
        val checksumSha256: String,
    )

    suspend fun forceWrite(
        storage: BackupStorageProvider,
        inspection: SlotInspectionResult,
        exportAction: suspend () -> BackupExportResult,
        metadataFactory: BackupSnapshotMetadataFactory,
    ): Result {
        return try {
            BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection)?.let {
                return Result.BlockedHardRefusal
            }
            if (BackupPhysicalSlotSafetyPolicy.hasValidAndUnreadable(inspection)) {
                return Result.BlockedHardRefusal
            }

            val export = exportAction()
            val payload: PraktikaBackupPayload = when (export) {
                is BackupExportResult.DatabaseUnsafe -> return Result.UnsafeDatabase
                is BackupExportResult.ReadFailure -> return Result.ExportFailed
                is BackupExportResult.Success -> export.payload
            }

            when (val plan = BackupPhysicalSlotSafetyPolicy.planWriteAfterSemanticDiff(inspection)) {
                BackupPhysicalSlotSafetyPolicy.WritePlan.SequenceExhausted ->
                    Result.SequenceExhausted

                is BackupPhysicalSlotSafetyPolicy.WritePlan.Ready ->
                    writeVerified(
                        storage = storage,
                        payload = payload,
                        targetSlot = plan.targetSlot,
                        nextSequence = plan.nextSequence,
                        metadataFactory = metadataFactory,
                    )
            }
        } catch (cancel: CancellationException) {
            throw cancel
        }
    }

    private suspend fun writeVerified(
        storage: BackupStorageProvider,
        payload: PraktikaBackupPayload,
        targetSlot: com.me4hik.praktika.data.backup.model.BackupSlotId,
        nextSequence: Long,
        metadataFactory: BackupSnapshotMetadataFactory,
    ): Result {
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
                    is BackupSlotWriteResult.Success ->
                        Result.Written(
                            ReceiptParts(
                                slot = targetSlot,
                                sequence = assembled.envelope.backupSequence,
                                createdAtEpochMillis = assembled.envelope.createdAtEpochMillis,
                                checksumSha256 = assembled.envelope.backupChecksumSha256,
                            ),
                        )

                    else -> Result.WriteFailed
                }
            }

            else -> Result.WriteFailed
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
