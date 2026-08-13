// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - validated slot candidate for A/B selection
package com.me4hik.praktika.data.backup.slot

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult

data class BackupSlotCandidate(
    val slotId: BackupSlotId,
    val validation: BackupFormatValidationResult,
) {
    val envelope: PraktikaBackupEnvelope?
        get() = (validation as? BackupFormatValidationResult.Valid)?.envelope
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
