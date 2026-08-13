package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPhysicalSlotSafetyPolicyTest {
    @Test
    fun ambiguousSlot_hardRefusal() {
        val inspection = SlotInspectionResult(
            slotA = ambiguousRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        assertEquals(BackupOutcome.AmbiguousSlot, BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection))
    }

    @Test
    fun bothInvalid_hardRefusal() {
        val inspection = SlotInspectionResult(
            slotA = invalidRead(BackupSlotId.A),
            slotB = invalidRead(BackupSlotId.B),
        )
        assertEquals(BackupOutcome.BothSlotsInvalid, BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection))
    }

    @Test
    fun invalidAndTooLarge_hardRefusalUntrustedArtifacts() {
        val inspection = SlotInspectionResult(
            slotA = invalidRead(BackupSlotId.A),
            slotB = tooLargeRead(BackupSlotId.B),
        )
        assertEquals(
            BackupOutcome.UntrustedArtifactsPresent,
            BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection),
        )
    }

    @Test
    fun invalidMissing_notHardRefusal() {
        val inspection = SlotInspectionResult(
            slotA = invalidRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        assertNull(BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection))
    }

    @Test
    fun unreadableMissing_hardRefusalWhenNoValid() {
        val inspection = SlotInspectionResult(
            slotA = unreadableRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        assertEquals(BackupOutcome.StorageReadFailed, BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection))
    }

    @Test
    fun validUnreadable_notHardRefusal() {
        val inspection = SlotInspectionResult(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
            slotB = unreadableRead(BackupSlotId.B),
        )
        assertNull(BackupPhysicalSlotSafetyPolicy.classifyHardRefusal(inspection))
        assertTrue(BackupPhysicalSlotSafetyPolicy.hasValidAndUnreadable(inspection))
    }

    @Test
    fun trueEmpty_planWriteASequence1() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Missing,
            slotB = SlotReadResult.Missing,
        )
        val plan = BackupPhysicalSlotSafetyPolicy.planWriteAfterSemanticDiff(inspection)
        assertEquals(
            BackupPhysicalSlotSafetyPolicy.WritePlan.Ready(BackupSlotId.A, 1L),
            plan,
        )
    }

    @Test
    fun invalidMissing_planWriteOppositeSequence1() {
        val inspection = SlotInspectionResult(
            slotA = invalidRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        val plan = BackupPhysicalSlotSafetyPolicy.planWriteAfterSemanticDiff(inspection)
        assertEquals(
            BackupPhysicalSlotSafetyPolicy.WritePlan.Ready(BackupSlotId.B, 1L),
            plan,
        )
    }

    @Test
    fun tooLargeMissing_planWriteOppositeSequence1() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Missing,
            slotB = tooLargeRead(BackupSlotId.B),
        )
        val plan = BackupPhysicalSlotSafetyPolicy.planWriteAfterSemanticDiff(inspection)
        assertEquals(
            BackupPhysicalSlotSafetyPolicy.WritePlan.Ready(BackupSlotId.A, 1L),
            plan,
        )
    }
}
