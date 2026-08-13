// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason

sealed class SlotReadResult {
    data object Missing : SlotReadResult()

    data class Valid(
        val slot: BackupSlotId,
        val envelope: PraktikaBackupEnvelope,
    ) : SlotReadResult()

    data class Invalid(
        val slot: BackupSlotId,
        val reason: BackupFormatFailureReason,
    ) : SlotReadResult()

    data class Unreadable(
        val slot: BackupSlotId,
        val exceptionClass: String,
    ) : SlotReadResult()

    data class Ambiguous(
        val slot: BackupSlotId,
        val matchCount: Int,
    ) : SlotReadResult()

    data class TooLarge(
        val slot: BackupSlotId,
    ) : SlotReadResult()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
