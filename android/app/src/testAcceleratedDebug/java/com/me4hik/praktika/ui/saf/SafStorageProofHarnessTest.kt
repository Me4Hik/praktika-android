package com.me4hik.praktika.ui.saf

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafStorageProofGateTest {
    private val emptyInspection = SlotInspectionResult(
        slotA = SlotReadResult.Missing,
        slotB = SlotReadResult.Missing,
    )

    @Test
    fun emptyFolder_enablesRun() {
        assertTrue(SafStorageProofGate.canRunProof(emptyInspection))
    }

    @Test
    fun nonEmptyA_blocksRun() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Valid(
                slot = BackupSlotId.A,
                envelope = envelope(sequence = 1L),
            ),
            slotB = SlotReadResult.Missing,
        )
        assertFalse(SafStorageProofGate.canRunProof(inspection))
    }

    @Test
    fun nonEmptyB_blocksRun() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Missing,
            slotB = SlotReadResult.Valid(
                slot = BackupSlotId.B,
                envelope = envelope(sequence = 2L),
            ),
        )
        assertFalse(SafStorageProofGate.canRunProof(inspection))
    }

    @Test
    fun ambiguous_blocksRun() {
        val inspection = SlotInspectionResult(
            slotA = SlotReadResult.Ambiguous(slot = BackupSlotId.A, matchCount = 2),
            slotB = SlotReadResult.Missing,
        )
        assertFalse(SafStorageProofGate.canRunProof(inspection))
        assertTrue(SafStorageProofGate.hasAmbiguousSlot(inspection))
    }

    private fun envelope(sequence: Long): PraktikaBackupEnvelope {
        return SyntheticBackupEnvelopeFactory().create(sequence)!!.envelope
    }
}

class SafStorageProofOrchestratorTest {
    private val orchestrator = SafStorageProofOrchestrator(SyntheticBackupEnvelopeFactory())

    @Test
    fun proofSequence_expectedSlotsAndSequences() = runTest {
        val storage = InMemorySafProofStorage()

        val result = orchestrator.runProof(storage)

        assertTrue(result is SafStorageProofRunResult.Passed)
        result as SafStorageProofRunResult.Passed
        assertEquals(3L, result.aSequence)
        assertEquals(2L, result.bSequence)
        assertEquals(BackupSlotId.A, result.latest)

        val finalInspection = storage.inspectSlots()
        assertTrue(
            SafStorageProofGate.matchesExpected(
                finalInspection,
                expectedA = SlotExpectation.ValidSequence(3L),
                expectedB = SlotExpectation.ValidSequence(2L),
            ),
        )
    }

    @Test
    fun proofStopsOnA1Failure() = runTest {
        val storage = InMemorySafProofStorage().apply {
            failOnSlot = BackupSlotId.A
        }

        val result = orchestrator.runProof(storage)

        assertTrue(result is SafStorageProofRunResult.Failed)
        assertEquals(
            SafStorageProofFailure.A1WriteFailed,
            (result as SafStorageProofRunResult.Failed).failure,
        )
    }

    @Test
    fun proofStopsOnB2Failure() = runTest {
        val storage = object : InMemorySafProofStorage() {
            override suspend fun writeSlot(
                slot: BackupSlotId,
                bytes: ByteArray,
                expectedEnvelope: PraktikaBackupEnvelope,
            ) = if (slot == BackupSlotId.B && expectedEnvelope.backupSequence == 2L) {
                BackupSlotWriteResult.CreateFailed(slot)
            } else {
                super.writeSlot(slot, bytes, expectedEnvelope)
            }
        }

        val result = orchestrator.runProof(storage)

        assertTrue(result is SafStorageProofRunResult.Failed)
        assertEquals(
            SafStorageProofFailure.B2WriteFailed,
            (result as SafStorageProofRunResult.Failed).failure,
        )
    }

    @Test
    fun proofStopsOnA3Failure() = runTest {
        val storage = object : InMemorySafProofStorage() {
            override suspend fun writeSlot(
                slot: BackupSlotId,
                bytes: ByteArray,
                expectedEnvelope: PraktikaBackupEnvelope,
            ) = if (slot == BackupSlotId.A && expectedEnvelope.backupSequence == 3L) {
                BackupSlotWriteResult.DeleteFailed(slot)
            } else {
                super.writeSlot(slot, bytes, expectedEnvelope)
            }
        }

        val result = orchestrator.runProof(storage)

        assertTrue(result is SafStorageProofRunResult.Failed)
        assertEquals(
            SafStorageProofFailure.A3WriteFailed,
            (result as SafStorageProofRunResult.Failed).failure,
        )
    }

    @Test
    fun selectorMustReturnA3() = runTest {
        val storage = InMemorySafProofStorage()
        val result = orchestrator.runProof(storage)

        assertTrue(result is SafStorageProofRunResult.Passed)
        assertEquals(BackupSlotId.A, (result as SafStorageProofRunResult.Passed).latest)
    }

    @Test
    fun proofStopsWhenFolderNotEmpty() = runTest {
        val storage = InMemorySafProofStorage()
        val preloaded = SyntheticBackupEnvelopeFactory().create(9L)!!.envelope
        storage.writeSlot(BackupSlotId.A, ByteArray(0), preloaded)

        val result = orchestrator.runProof(storage)

        assertTrue(result is SafStorageProofRunResult.Failed)
        assertEquals(
            SafStorageProofFailure.FolderNotEmpty,
            (result as SafStorageProofRunResult.Failed).failure,
        )
    }
}

class SyntheticBackupEnvelopeFactoryTest {
    @Test
    fun createsValidEnvelopesForSequences() {
        val factory = SyntheticBackupEnvelopeFactory()
        listOf(1L, 2L, 3L).forEach { sequence ->
            val bundle = factory.create(sequence)
            assertTrue(bundle != null)
            assertEquals(sequence, bundle!!.envelope.backupSequence)
            assertTrue(bundle.bytes.isNotEmpty())
        }
    }
}
