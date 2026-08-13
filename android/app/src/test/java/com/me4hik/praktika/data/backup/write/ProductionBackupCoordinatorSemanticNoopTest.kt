package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionBackupCoordinatorSemanticNoopTest {
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload
    private val trustedCreatedAt = 1_700_000_300_000L

    @Test
    fun validLatestMetadataDiffButPayloadEqual_noChange() = runTest {
        val latestEnvelope = envelopeWithSequence(
            sequence = 99L,
            versionCode = 99,
            versionName = "9.9-metadata-only",
        )
        val storage = RecordingBackupStorage(
            slotB = validRead(BackupSlotId.B, latestEnvelope),
        )
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = goldenPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.NoChange(trustedCreatedAt), outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun validInvalidOppositeEqual_noChangePreservesInvalid() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
            slotB = invalidRead(BackupSlotId.B),
        )
        val preservedInvalid = storage.slotB
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = goldenPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.NoChange(trustedCreatedAt), outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(preservedInvalid, storage.slotB)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun validTooLargeOppositeEqual_noChangePreservesTooLarge() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
            slotB = tooLargeRead(BackupSlotId.B),
        )
        val preserved = storage.slotB
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = goldenPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.NoChange(trustedCreatedAt), outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(preserved, storage.slotB)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun validUnreadableEqual_noChange() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
            slotB = unreadableRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = goldenPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.NoChange(trustedCreatedAt), outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun unreadableValidEqual_noChange() = runTest {
        val storage = RecordingBackupStorage(
            slotA = unreadableRead(BackupSlotId.A),
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(10L)),
        )
        storage.snapshotInspectionState()
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = goldenPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.NoChange(trustedCreatedAt), outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun validUnreadableDifferent_storageAccessUnreliable() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(10L)),
            slotB = unreadableRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 5)
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.StorageAccessUnreliable, outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }

    @Test
    fun unreadableValidDifferent_storageAccessUnreliable() = runTest {
        val storage = RecordingBackupStorage(
            slotA = unreadableRead(BackupSlotId.A),
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(10L)),
        )
        storage.snapshotInspectionState()
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 6)
        val (coordinator, clock) = coordinatorForTest(this, storage, exportPayload = changedPayload)

        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)

        assertEquals(BackupOutcome.StorageAccessUnreliable, outcome)
        assertZeroWrites(storage, outcome)
        assertEquals(0, clock.captureCount)
    }
}
