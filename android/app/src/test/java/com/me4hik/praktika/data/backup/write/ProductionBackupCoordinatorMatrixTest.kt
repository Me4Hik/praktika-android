package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupDatabaseUnsafeReason
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionBackupCoordinatorMatrixTest {
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload

    private fun successWrite(slot: BackupSlotId, sequence: Long): BackupSlotWriteResult {
        return BackupSlotWriteResult.Success(
            slot = slot,
            sequence = sequence,
            checksum = "00",
        )
    }

    @Test
    fun missingMissing_writesASequence1() = runTest {
        val storage = RecordingBackupStorage()
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.A, 1L)
        val (coordinator, clock) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.A, 1L, outcome)
        assertEquals(1, clock.captureCount)
    }

    @Test
    fun validMissing_diff_writesBSequencePlusOne() = runTest {
        val changedPayload = payloadWithExtraAnswer(
            goldenPayload,
            BackupAnswer(
                cycleNumber = 1,
                cyclePosition = 2,
                text = "new answer",
                createdAtEpochMillis = 1L,
            ),
        )
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
        )
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.B, 11L)
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.B, 11L, outcome)
    }

    @Test
    fun missingValid_diff_writesASequencePlusOne() = runTest {
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 99)
        val storage = RecordingBackupStorage(
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(20L)),
        )
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.A, 21L)
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.A, 21L, outcome)
    }

    @Test
    fun validValid_diff_rotatesUsingFrozenPolicy() = runTest {
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 9)
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(12L)),
        )
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.A, 13L)
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.A, 13L, outcome)
    }

    @Test
    fun validInvalid_diff_overwritesInvalidSlot() = runTest {
        val changedPayload = practiceStatePaused(goldenPayload, pausedAtEpochMillis = 123L)
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(5L)),
            slotB = invalidRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.B, 6L)
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.B, 6L, outcome)
    }

    @Test
    fun invalidValid_diff_overwritesInvalidSlot() = runTest {
        val changedPayload = practiceStatePaused(goldenPayload, pausedAtEpochMillis = 456L)
        val storage = RecordingBackupStorage(
            slotA = invalidRead(BackupSlotId.A),
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(8L)),
        )
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.A, 9L)
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.A, 9L, outcome)
    }

    @Test
    fun bothInvalid_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage(
            slotA = invalidRead(BackupSlotId.A),
            slotB = invalidRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.BothSlotsInvalid, outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun invalidMissing_writesOppositePreservesInvalid() = runTest {
        val storage = RecordingBackupStorage(
            slotA = invalidRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        val preservedInvalid = storage.slotA
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.B, 1L)
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.B, 1L, outcome)
        assertEquals(preservedInvalid, storage.slotA)
    }

    @Test
    fun missingInvalid_writesOppositePreservesInvalid() = runTest {
        val storage = RecordingBackupStorage(
            slotA = SlotReadResult.Missing,
            slotB = invalidRead(BackupSlotId.B),
        )
        val preservedInvalid = storage.slotB
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.A, 1L)
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.A, 1L, outcome)
        assertEquals(preservedInvalid, storage.slotB)
    }

    @Test
    fun tooLargeMissing_writesOppositePreservesTooLarge() = runTest {
        val storage = RecordingBackupStorage(
            slotA = tooLargeRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        val preserved = storage.slotA
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.B, 1L)
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.B, 1L, outcome)
        assertEquals(preserved, storage.slotA)
    }

    @Test
    fun missingTooLarge_writesOppositePreservesTooLarge() = runTest {
        val storage = RecordingBackupStorage(
            slotA = SlotReadResult.Missing,
            slotB = tooLargeRead(BackupSlotId.B),
        )
        val preserved = storage.slotB
        storage.snapshotInspectionState()
        storage.writeResult = successWrite(BackupSlotId.A, 1L)
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertSingleWrite(storage, BackupSlotId.A, 1L, outcome)
        assertEquals(preserved, storage.slotB)
    }

    @Test
    fun invalidTooLarge_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage(
            slotA = invalidRead(BackupSlotId.A),
            slotB = tooLargeRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.UntrustedArtifactsPresent, outcome)
        assertZeroWrites(storage, outcome)
    }

    @Test
    fun tooLargeTooLarge_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage(
            slotA = tooLargeRead(BackupSlotId.A),
            slotB = tooLargeRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.UntrustedArtifactsPresent, outcome)
        assertZeroWrites(storage, outcome)
    }

    @Test
    fun ambiguousSlot_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(3L)),
            slotB = ambiguousRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.AmbiguousSlot, outcome)
        assertZeroWrites(storage, outcome)
    }

    @Test
    fun unreadableMissing_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage(
            slotA = unreadableRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        storage.snapshotInspectionState()
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.StorageReadFailed, outcome)
        assertZeroWrites(storage, outcome)
    }

    @Test
    fun missingUnreadable_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage(
            slotA = SlotReadResult.Missing,
            slotB = unreadableRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val (coordinator, _) = coordinatorForTest(this, storage)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.StorageReadFailed, outcome)
        assertZeroWrites(storage, outcome)
    }

    @Test
    fun sequenceExhaustion_refusesWithZeroWrites() = runTest {
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 4)
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(Long.MAX_VALUE)),
        )
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.SequenceExhausted, outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun unsafeDatabase_refusesWithZeroWrites() = runTest {
        val storage = RecordingBackupStorage()
        storage.snapshotInspectionState()
        val metadataFactory = BackupSnapshotMetadataFactory(
            clock = CountingBackupClock(),
            appMetadataProvider = FixedBackupAppMetadataProvider(),
        )
        val coordinator = ProductionBackupCoordinator(
            storage = storage,
            exportAction = {
                BackupExportResult.DatabaseUnsafe(BackupDatabaseUnsafeReason.MULTIPLE_INCOMPLETE_OCCURRENCES)
            },
            metadataFactory = metadataFactory,
            scope = this,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.UnsafeDatabaseState, outcome)
        assertZeroWrites(storage, outcome)
    }

    @Test
    fun postWriteVerificationFailure_returnsWriteFailed() = runTest {
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 8)
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(2L)),
        )
        storage.snapshotInspectionState()
        storage.writeResult = BackupSlotWriteResult.PostWriteValidationFailed(BackupSlotId.B)
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(
            BackupOutcome.WriteFailed(BackupWriteFailureCategory.POST_WRITE_VALIDATION_FAILED),
            outcome,
        )
        assertEquals(1, storage.writeCalls.size)
    }

    @Test
    fun cancellationDuringWrite_propagates() = runTest {
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 7)
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(4L)),
        )
        storage.writeThrows = CancellationException("cancelled")
        val (coordinator, _) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        try {
            coordinator.backupNow(BackupRequestReason.MANUAL)
            error("Expected CancellationException")
        } catch (cancel: CancellationException) {
            assertEquals("cancelled", cancel.message)
        }
    }
}
