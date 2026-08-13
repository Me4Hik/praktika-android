package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProductionBackupCoordinatorConcurrencyTest {
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload

    private class GatedRecordingStorage(
        slotA: SlotReadResult = SlotReadResult.Missing,
        slotB: SlotReadResult = SlotReadResult.Missing,
    ) : RecordingBackupStorage(slotA, slotB) {
        val writeGate = CompletableDeferred<Unit>()

        override suspend fun writeSlot(
            slot: BackupSlotId,
            bytes: ByteArray,
            expectedEnvelope: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
        ): BackupSlotWriteResult {
            writeGate.await()
            return super.writeSlot(slot, bytes, expectedEnvelope)
        }
    }

    @Test
    fun concurrentBackupNow_singleWriter() = runTest {
        val activeWriters = AtomicInteger(0)
        var maxConcurrent = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = object : RecordingBackupStorage(
            slotA = SlotReadResult.Missing,
            slotB = SlotReadResult.Missing,
        ) {
            override suspend fun writeSlot(
                slot: BackupSlotId,
                bytes: ByteArray,
                expectedEnvelope: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
            ): BackupSlotWriteResult {
                val active = activeWriters.incrementAndGet()
                if (active > maxConcurrent) {
                    maxConcurrent = active
                }
                delay(25L)
                val result = super.writeSlot(slot, bytes, expectedEnvelope)
                activeWriters.decrementAndGet()
                return result
            }
        }
        storage.writeResult = BackupSlotWriteResult.Success(
            slot = BackupSlotId.A,
            sequence = 1L,
            checksum = "00",
        )
        val changedPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 15)
        val (coordinator, _) = coordinatorForTest(
            scope = this,
            storage = storage,
            exportPayload = changedPayload,
            dispatcher = dispatcher,
        )

        val jobs = (1..4).map {
            async { coordinator.backupNow(BackupRequestReason.MANUAL) }
        }
        advanceUntilIdle()
        jobs.awaitAll()

        assertEquals(1, maxConcurrent)
        assertTrue(storage.writeCalls.isNotEmpty())
    }

    @Test
    fun requestBackupCoalescesToLatestState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var exportPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 41)
        var exportCount = 0
        val storage = GatedRecordingStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(1L)),
        )
        storage.writeResult = BackupSlotWriteResult.Success(
            slot = BackupSlotId.B,
            sequence = 2L,
            checksum = "00",
        )
        val metadataFactory = BackupSnapshotMetadataFactory(
            clock = CountingBackupClock(),
            appMetadataProvider = FixedBackupAppMetadataProvider(),
        )
        val coordinator = ProductionBackupCoordinator(
            storage = storage,
            exportAction = {
                exportCount += 1
                BackupExportResult.Success(exportPayload)
            },
            metadataFactory = metadataFactory,
            scope = this,
            ioDispatcher = dispatcher,
        )

        val job = async(dispatcher) {
            coordinator.requestBackup(BackupRequestReason.MANUAL)
        }
        advanceUntilIdle()
        exportPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 42)
        coordinator.requestBackup(BackupRequestReason.MANUAL)
        coordinator.requestBackup(BackupRequestReason.MANUAL)
        storage.writeGate.complete(Unit)
        advanceUntilIdle()
        job.await()

        assertEquals(2, exportCount)
        assertTrue(storage.writeCalls.isNotEmpty())
        assertEquals(3L, storage.writeCalls.last().sequence)
        assertTrue(storage.writeCalls.size <= 2)
    }

    @Test
    fun requestDuringNoChangeEventuallyWritesWhenStateChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var exportPayload = goldenPayload
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(5L)),
        )
        storage.writeResult = BackupSlotWriteResult.Success(
            slot = BackupSlotId.B,
            sequence = 6L,
            checksum = "00",
        )
        val metadataFactory = BackupSnapshotMetadataFactory(
            clock = CountingBackupClock(),
            appMetadataProvider = FixedBackupAppMetadataProvider(),
        )
        val coordinator = ProductionBackupCoordinator(
            storage = storage,
            exportAction = { BackupExportResult.Success(exportPayload) },
            metadataFactory = metadataFactory,
            scope = this,
            ioDispatcher = dispatcher,
        )

        val firstOutcome = coordinator.backupNow(BackupRequestReason.MANUAL)
        assertEquals(BackupOutcome.NoChange(1_700_000_300_000L), firstOutcome)

        exportPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 12)
        coordinator.requestBackup(BackupRequestReason.MANUAL)
        advanceUntilIdle()

        assertEquals(1, storage.writeCalls.size)
        assertEquals(6L, storage.writeCalls.single().sequence)
    }
}
