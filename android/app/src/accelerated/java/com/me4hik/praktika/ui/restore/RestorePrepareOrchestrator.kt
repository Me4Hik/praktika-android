package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.slot.BackupSlotCandidate
import com.me4hik.praktika.data.backup.slot.BackupSlotSelector
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import kotlin.coroutines.cancellation.CancellationException

enum class RestorePrepareFailure(val code: String) {
    FolderNotEmpty("RESTORE_TEST_FOLDER_NOT_EMPTY_DURING_PREPARE"),
    AmbiguousSlot("UNSAFE_TEST_FOLDER_AMBIGUOUS"),
    EnvelopeAssemblyFailed("RESTORE_TEST_BACKUP_PREPARE_FAILED"),
    AWriteFailed("RESTORE_TEST_BACKUP_PREPARE_FAILED"),
    AInspectMismatch("RESTORE_TEST_BACKUP_PREPARE_FAILED"),
    BWriteFailed("RESTORE_TEST_BACKUP_PREPARE_FAILED"),
    BInspectMismatch("RESTORE_TEST_BACKUP_PREPARE_FAILED"),
    FinalInspectMismatch("RESTORE_TEST_BACKUP_PREPARE_FAILED"),
    SelectorMismatch("BACKUP_SELECTION_FAILED"),
}

sealed class RestorePrepareResult {
    data class Prepared(
        val latestSlot: BackupSlotId,
    ) : RestorePrepareResult()

    data class Failed(
        val failure: RestorePrepareFailure,
        val detail: String? = null,
    ) : RestorePrepareResult()
}

class RestorePrepareOrchestrator(
    private val envelopeFactory: RichRestoreAcceptanceEnvelopeFactory,
) {
    suspend fun prepare(storage: BackupStorageProvider): RestorePrepareResult {
        return try {
            prepareInternal(storage)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            RestorePrepareResult.Failed(
                failure = RestorePrepareFailure.EnvelopeAssemblyFailed,
                detail = exception.javaClass.name,
            )
        }
    }

    private suspend fun prepareInternal(storage: BackupStorageProvider): RestorePrepareResult {
        val preInspection = storage.inspectSlots()
        if (RestorePrepareGate.hasAmbiguousSlot(preInspection)) {
            return RestorePrepareResult.Failed(RestorePrepareFailure.AmbiguousSlot)
        }
        if (!RestorePrepareGate.canPrepare(preInspection)) {
            return RestorePrepareResult.Failed(RestorePrepareFailure.FolderNotEmpty)
        }

        writeSlot(storage, BackupSlotId.A, envelopeFactory::createSlotA)?.let { return it }

        val afterA = storage.inspectSlots()
        if (
            afterA.slotA !is SlotReadResult.Valid ||
            afterA.slotA.envelope.backupSequence != 1L ||
            afterA.slotB !is SlotReadResult.Missing
        ) {
            return RestorePrepareResult.Failed(RestorePrepareFailure.AInspectMismatch)
        }

        writeSlot(storage, BackupSlotId.B, envelopeFactory::createSlotB)?.let { return it }

        val finalInspection = storage.inspectSlots()
        if (!RestorePrepareGate.matchesExpectedFinal(finalInspection)) {
            return RestorePrepareResult.Failed(RestorePrepareFailure.FinalInspectMismatch)
        }

        val latest = selectLatest(finalInspection)
            ?: return RestorePrepareResult.Failed(RestorePrepareFailure.SelectorMismatch)
        if (latest != BackupSlotId.B) {
            return RestorePrepareResult.Failed(RestorePrepareFailure.SelectorMismatch)
        }

        return RestorePrepareResult.Prepared(latestSlot = latest)
    }

    private suspend fun writeSlot(
        storage: BackupStorageProvider,
        slot: BackupSlotId,
        bundleFactory: () -> com.me4hik.praktika.ui.saf.SyntheticEnvelopeBundle?,
    ): RestorePrepareResult.Failed? {
        val bundle = bundleFactory()
            ?: return RestorePrepareResult.Failed(RestorePrepareFailure.EnvelopeAssemblyFailed)

        return when (
            storage.writeSlot(
                slot = slot,
                bytes = bundle.bytes,
                expectedEnvelope = bundle.envelope,
            )
        ) {
            is BackupSlotWriteResult.Success -> null
            else -> RestorePrepareResult.Failed(
                failure = when (slot) {
                    BackupSlotId.A -> RestorePrepareFailure.AWriteFailed
                    BackupSlotId.B -> RestorePrepareFailure.BWriteFailed
                },
            )
        }
    }

    private fun selectLatest(inspection: com.me4hik.praktika.data.backup.storage.SlotInspectionResult): BackupSlotId? {
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
