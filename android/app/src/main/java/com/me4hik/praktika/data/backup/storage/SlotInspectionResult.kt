// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.BackupSlotId

data class SlotInspectionResult(
    val slotA: SlotReadResult,
    val slotB: SlotReadResult,
) {
    fun resultFor(slot: BackupSlotId): SlotReadResult = when (slot) {
        BackupSlotId.A -> slotA
        BackupSlotId.B -> slotB
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
