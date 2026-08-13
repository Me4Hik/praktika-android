package com.me4hik.praktika.ui.saf

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult

open class InMemorySafProofStorage : BackupStorageProvider {
    private val envelopes = mutableMapOf<BackupSlotId, PraktikaBackupEnvelope>()

    open var failOnSlot: BackupSlotId? = null

    override suspend fun inspectSlots(): SlotInspectionResult {
        return SlotInspectionResult(
            slotA = readInternal(BackupSlotId.A),
            slotB = readInternal(BackupSlotId.B),
        )
    }

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult = readInternal(slot)

    open override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult {
        if (failOnSlot == slot) {
            return BackupSlotWriteResult.CreateFailed(slot)
        }
        envelopes[slot] = expectedEnvelope
        return BackupSlotWriteResult.Success(
            slot = slot,
            sequence = expectedEnvelope.backupSequence,
            checksum = expectedEnvelope.backupChecksumSha256,
        )
    }

    private fun readInternal(slot: BackupSlotId): SlotReadResult {
        val envelope = envelopes[slot] ?: return SlotReadResult.Missing
        return SlotReadResult.Valid(slot = slot, envelope = envelope)
    }
}
