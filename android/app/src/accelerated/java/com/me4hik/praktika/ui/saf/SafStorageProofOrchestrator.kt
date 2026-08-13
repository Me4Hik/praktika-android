package com.me4hik.praktika.ui.saf

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.slot.BackupSlotCandidate
import com.me4hik.praktika.data.backup.slot.BackupSlotSelector
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class SafStorageProofOrchestrator(
    private val envelopeFactory: SyntheticBackupEnvelopeFactory,
) {
    suspend fun runProof(storage: BackupStorageProvider): SafStorageProofRunResult {
        return try {
            runProofInternal(storage)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            SafStorageProofRunResult.Failed(
                failure = SafStorageProofFailure.ProviderError,
                detail = exception.javaClass.name,
            )
        }
    }

    private suspend fun runProofInternal(storage: BackupStorageProvider): SafStorageProofRunResult {
        coroutineContext.ensureActive()

        val preRunInspection = storage.inspectSlots()
        if (SafStorageProofGate.hasAmbiguousSlot(preRunInspection)) {
            return SafStorageProofRunResult.Failed(SafStorageProofFailure.AmbiguousSlot)
        }
        if (!SafStorageProofGate.isEmptyFolder(preRunInspection)) {
            return SafStorageProofRunResult.Failed(SafStorageProofFailure.FolderNotEmpty)
        }

        writeStep(storage, BackupSlotId.A, sequence = 1L, stepName = "A1")?.let { return it }

        val afterA1 = storage.inspectSlots()
        if (
            !SafStorageProofGate.matchesExpected(
                afterA1,
                expectedA = SlotExpectation.ValidSequence(1L),
                expectedB = SlotExpectation.Missing,
            )
        ) {
            SafStorageProofLogger.proofStep("A1", "FAIL")
            return SafStorageProofRunResult.Failed(SafStorageProofFailure.A1InspectMismatch)
        }
        SafStorageProofLogger.proofStep("A1", "PASS")

        writeStep(storage, BackupSlotId.B, sequence = 2L, stepName = "B2")?.let { return it }

        val afterB2 = storage.inspectSlots()
        if (
            !SafStorageProofGate.matchesExpected(
                afterB2,
                expectedA = SlotExpectation.ValidSequence(1L),
                expectedB = SlotExpectation.ValidSequence(2L),
            )
        ) {
            SafStorageProofLogger.proofStep("B2", "FAIL")
            return SafStorageProofRunResult.Failed(SafStorageProofFailure.B2InspectMismatch)
        }
        SafStorageProofLogger.proofStep("B2", "PASS")

        writeStep(storage, BackupSlotId.A, sequence = 3L, stepName = "A3")?.let { return it }

        val afterA3 = storage.inspectSlots()
        if (
            !SafStorageProofGate.matchesExpected(
                afterA3,
                expectedA = SlotExpectation.ValidSequence(3L),
                expectedB = SlotExpectation.ValidSequence(2L),
            )
        ) {
            SafStorageProofLogger.proofStep("A3", "FAIL")
            return SafStorageProofRunResult.Failed(SafStorageProofFailure.A3InspectMismatch)
        }
        SafStorageProofLogger.proofStep("A3", "PASS")
        SafStorageProofLogger.documentUriRediscoveryWorked(yes = true)

        val latest = selectLatest(afterA3) ?: return SafStorageProofRunResult.Failed(
            SafStorageProofFailure.SelectorMismatch,
        )
        if (latest != BackupSlotId.A) {
            SafStorageProofLogger.proofFailure(SafStorageProofFailure.SelectorMismatch.code)
            return SafStorageProofRunResult.Failed(SafStorageProofFailure.SelectorMismatch)
        }
        SafStorageProofLogger.proofLatest("A")

        return SafStorageProofRunResult.Passed(
            aSequence = 3L,
            bSequence = 2L,
            latest = latest,
            documentUriRediscoveryWorked = true,
        )
    }

    private suspend fun writeStep(
        storage: BackupStorageProvider,
        slot: BackupSlotId,
        sequence: Long,
        stepName: String,
    ): SafStorageProofRunResult.Failed? {
        coroutineContext.ensureActive()
        val bundle = envelopeFactory.create(sequence)
            ?: return SafStorageProofRunResult.Failed(SafStorageProofFailure.EnvelopeAssemblyFailed)

        return when (
            val writeResult = storage.writeSlot(
                slot = slot,
                bytes = bundle.bytes,
                expectedEnvelope = bundle.envelope,
            )
        ) {
            is BackupSlotWriteResult.Success -> null
            else -> {
                SafStorageProofLogger.proofStep(stepName, "FAIL")
                SafStorageProofRunResult.Failed(
                    failure = writeFailureForStep(stepName),
                    detail = writeResult.javaClass.simpleName,
                )
            }
        }
    }

    private fun writeFailureForStep(stepName: String): SafStorageProofFailure = when (stepName) {
        "A1" -> SafStorageProofFailure.A1WriteFailed
        "B2" -> SafStorageProofFailure.B2WriteFailed
        else -> SafStorageProofFailure.A3WriteFailed
    }

    private fun selectLatest(inspection: SlotInspectionResult): BackupSlotId? {
        val candidates = listOf(
            inspection.slotA.toCandidate(BackupSlotId.A),
            inspection.slotB.toCandidate(BackupSlotId.B),
        )
        return BackupSlotSelector.selectBest(candidates)
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
