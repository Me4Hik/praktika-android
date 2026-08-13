// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

interface BackupStorageProvider {
    suspend fun inspectSlots(): SlotInspectionResult

    suspend fun readSlot(slot: BackupSlotId): SlotReadResult

    suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
