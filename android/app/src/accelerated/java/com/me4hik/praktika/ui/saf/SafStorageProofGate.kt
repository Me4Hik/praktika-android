package com.me4hik.praktika.ui.saf

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult

object SafStorageProofGate {
    fun summarize(result: SlotReadResult): SlotSummary = when (result) {
        SlotReadResult.Missing -> SlotSummary.Missing
        is SlotReadResult.Valid -> SlotSummary.Valid(result.envelope.backupSequence)
        is SlotReadResult.Invalid -> SlotSummary.Invalid
        is SlotReadResult.Unreadable -> SlotSummary.Unreadable
        is SlotReadResult.Ambiguous -> SlotSummary.Ambiguous(result.matchCount)
        is SlotReadResult.TooLarge -> SlotSummary.TooLarge
    }

    fun isEmptyFolder(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Missing &&
            inspection.slotB is SlotReadResult.Missing
    }

    fun canRunProof(inspection: SlotInspectionResult): Boolean = isEmptyFolder(inspection)

    fun hasAmbiguousSlot(inspection: SlotInspectionResult): Boolean {
        return inspection.slotA is SlotReadResult.Ambiguous ||
            inspection.slotB is SlotReadResult.Ambiguous
    }

    fun matchesExpected(
        inspection: SlotInspectionResult,
        expectedA: SlotExpectation,
        expectedB: SlotExpectation,
    ): Boolean {
        return matchesSlot(inspection.slotA, expectedA) &&
            matchesSlot(inspection.slotB, expectedB)
    }

    private fun matchesSlot(result: SlotReadResult, expected: SlotExpectation): Boolean {
        return when (expected) {
            SlotExpectation.Missing -> result is SlotReadResult.Missing
            is SlotExpectation.ValidSequence -> {
                result is SlotReadResult.Valid && result.envelope.backupSequence == expected.sequence
            }
        }
    }
}

sealed class SlotExpectation {
    data object Missing : SlotExpectation()

    data class ValidSequence(val sequence: Long) : SlotExpectation()
}
