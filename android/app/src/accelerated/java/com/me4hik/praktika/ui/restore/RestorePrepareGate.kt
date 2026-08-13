package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult

object RestorePrepareGate {
    fun isEmptyFolder(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Missing &&
            inspection.slotB is SlotReadResult.Missing
    }

    fun canPrepare(inspection: SlotInspectionResult): Boolean = isEmptyFolder(inspection)

    fun hasAmbiguousSlot(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Ambiguous ||
            inspection.slotB is SlotReadResult.Ambiguous
    }

    fun summarize(result: SlotReadResult): RestoreSlotSummary = when (result) {
        SlotReadResult.Missing -> RestoreSlotSummary.Missing
        is SlotReadResult.Valid -> RestoreSlotSummary.Valid(result.envelope.backupSequence)
        is SlotReadResult.Invalid -> RestoreSlotSummary.Invalid
        is SlotReadResult.Unreadable -> RestoreSlotSummary.Unreadable
        is SlotReadResult.Ambiguous -> RestoreSlotSummary.Ambiguous(result.matchCount)
        is SlotReadResult.TooLarge -> RestoreSlotSummary.TooLarge
    }

    fun matchesExpectedFinal(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Valid &&
            inspection.slotA.envelope.backupSequence == 1L &&
            inspection.slotB is SlotReadResult.Valid &&
            inspection.slotB.envelope.backupSequence == 2L
    }
}

sealed class RestoreSlotSummary {
    data object Missing : RestoreSlotSummary()

    data class Valid(val sequence: Long) : RestoreSlotSummary()

    data object Invalid : RestoreSlotSummary()

    data object Unreadable : RestoreSlotSummary()

    data class Ambiguous(val matchCount: Int) : RestoreSlotSummary()

    data object TooLarge : RestoreSlotSummary()

    fun displayText(slot: BackupSlotId): String = when (this) {
        Missing -> "${slot.label()}: Missing"
        is Valid -> "${slot.label()}: Valid(seq=$sequence)"
        Invalid -> "${slot.label()}: Invalid"
        Unreadable -> "${slot.label()}: Unreadable"
        is Ambiguous -> "${slot.label()}: Ambiguous(count=$matchCount)"
        TooLarge -> "${slot.label()}: TooLarge"
    }
}

private fun BackupSlotId.label(): String = when (this) {
    BackupSlotId.A -> "A"
    BackupSlotId.B -> "B"
}
