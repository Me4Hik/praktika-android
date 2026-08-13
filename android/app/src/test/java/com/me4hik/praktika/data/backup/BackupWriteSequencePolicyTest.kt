// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - backup write sequence policy JVM tests
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.slot.BackupWritePlan
import com.me4hik.praktika.data.backup.slot.BackupWriteSequencePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWriteSequencePolicyTest {
    @Test
    fun noSlots_nextA_sequence1() {
        val plan = BackupWriteSequencePolicy.planNextWrite(emptyList())
        assertTrue(plan is BackupWritePlan.Ready)
        plan as BackupWritePlan.Ready
        assertEquals(BackupSlotId.A, plan.targetSlot)
        assertEquals(1L, plan.nextSequence)
    }

    @Test
    fun latestA_nextB() {
        val latest = validEnvelope(slotId = BackupSlotId.A, sequence = 10L)
        val plan = BackupWriteSequencePolicy.planNextWrite(listOf(BackupSlotId.A to latest))
        assertTrue(plan is BackupWritePlan.Ready)
        plan as BackupWritePlan.Ready
        assertEquals(BackupSlotId.B, plan.targetSlot)
        assertEquals(11L, plan.nextSequence)
    }

    @Test
    fun latestB_nextA() {
        val latest = validEnvelope(slotId = BackupSlotId.B, sequence = 20L)
        val plan = BackupWriteSequencePolicy.planNextWrite(listOf(BackupSlotId.B to latest))
        assertTrue(plan is BackupWritePlan.Ready)
        plan as BackupWritePlan.Ready
        assertEquals(BackupSlotId.A, plan.targetSlot)
        assertEquals(21L, plan.nextSequence)
    }

    @Test
    fun sequenceMax_nextWriteSequenceExhausted() {
        val latest = validEnvelope(slotId = BackupSlotId.A, sequence = Long.MAX_VALUE)
        val plan = BackupWriteSequencePolicy.planNextWrite(listOf(BackupSlotId.A to latest))
        assertEquals(BackupWritePlan.SequenceExhausted, plan)
    }

    private fun validEnvelope(slotId: BackupSlotId, sequence: Long): PraktikaBackupEnvelope {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val envelope = PraktikaBackupEnvelope(
            backupSchemaVersion = base.backupSchemaVersion,
            backupSequence = sequence,
            createdAtEpochMillis = base.createdAtEpochMillis,
            sourceAppVersionCode = base.sourceAppVersionCode,
            sourceAppVersionName = base.sourceAppVersionName,
            sourceSeedVersion = base.sourceSeedVersion,
            backupChecksumSha256 = "",
            payload = base.payload,
        )
        val checksum = BackupChecksum.calculate(envelope)
        return PraktikaBackupEnvelope(
            backupSchemaVersion = envelope.backupSchemaVersion,
            backupSequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceAppVersionCode = envelope.sourceAppVersionCode,
            sourceAppVersionName = envelope.sourceAppVersionName,
            sourceSeedVersion = envelope.sourceSeedVersion,
            backupChecksumSha256 = checksum,
            payload = envelope.payload,
        )
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
