package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupAppMetadataProvider
import com.me4hik.praktika.data.backup.metadata.BackupClock
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.metadata.BackupSourceAppMetadata
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

internal class CountingBackupClock(
    private val fixedEpochMillis: Long = 1_800_000_000_000L,
) : BackupClock {
    var captureCount: Int = 0
        private set

    override fun nowEpochMillis(): Long {
        captureCount++
        return fixedEpochMillis
    }
}

internal class FixedBackupAppMetadataProvider(
    private val versionCode: Int = 7,
    private val versionName: String = "1.0",
) : BackupAppMetadataProvider {
    override fun current(): BackupSourceAppMetadata {
        return BackupSourceAppMetadata(
            versionCode = versionCode,
            versionName = versionName,
        )
    }
}

internal data class RecordedWriteCall(
    val slot: BackupSlotId,
    val byteCount: Int,
    val sequence: Long,
)

internal open class RecordingBackupStorage(
    slotA: SlotReadResult = SlotReadResult.Missing,
    slotB: SlotReadResult = SlotReadResult.Missing,
) : BackupStorageProvider {
    var slotA: SlotReadResult = slotA
        private set
    var slotB: SlotReadResult = slotB
        private set

    val writeCalls = mutableListOf<RecordedWriteCall>()
    var writeResult: BackupSlotWriteResult = BackupSlotWriteResult.CreateFailed(BackupSlotId.A)
    var writeDelayMillis: Long = 0L
    var writeThrows: CancellationException? = null
    var onWriteSuspend: (suspend () -> Unit)? = null
    var deleteCallCount: Int = 0

    private var inspectionSnapshot: SlotInspectionResult? = null

    fun snapshotInspectionState() {
        inspectionSnapshot = SlotInspectionResult(slotA = slotA, slotB = slotB)
    }

    fun inspectionUnchanged(): Boolean {
        val snapshot = inspectionSnapshot ?: return false
        return snapshot.slotA == slotA && snapshot.slotB == slotB
    }

    fun replaceSlotForTest(slot: BackupSlotId, result: SlotReadResult) {
        when (slot) {
            BackupSlotId.A -> slotA = result
            BackupSlotId.B -> slotB = result
        }
    }

    /** Test-only: simulate external file mutation / deletion attempts (never used by setup core). */
    fun recordDeleteAttemptForTest() {
        deleteCallCount++
    }

    override suspend fun inspectSlots(): SlotInspectionResult {
        return SlotInspectionResult(slotA = slotA, slotB = slotB)
    }

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult {
        return when (slot) {
            BackupSlotId.A -> slotA
            BackupSlotId.B -> slotB
        }
    }

    override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult {
        writeThrows?.let { throw it }
        onWriteSuspend?.invoke()
        if (writeDelayMillis > 0L) {
            delay(writeDelayMillis)
        }
        writeCalls += RecordedWriteCall(
            slot = slot,
            byteCount = bytes.size,
            sequence = expectedEnvelope.backupSequence,
        )
        when (writeResult) {
            is BackupSlotWriteResult.Success -> {
                val valid = SlotReadResult.Valid(slot = slot, envelope = expectedEnvelope)
                when (slot) {
                    BackupSlotId.A -> slotA = valid
                    BackupSlotId.B -> slotB = valid
                }
            }

            else -> Unit
        }
        return writeResult
    }
}

internal fun envelopeWithSequence(
    sequence: Long,
    payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload =
        BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload,
    versionCode: Int = 7,
    versionName: String = "1.0",
): PraktikaBackupEnvelope {
    val provisional = PraktikaBackupEnvelope(
        backupSchemaVersion = BackupGoldenFixtures.goldenEnvelopeWithChecksum().backupSchemaVersion,
        backupSequence = sequence,
        createdAtEpochMillis = 1_700_000_300_000L,
        sourceAppVersionCode = versionCode,
        sourceAppVersionName = versionName,
        sourceSeedVersion = 1,
        backupChecksumSha256 = "",
        payload = payload,
    )
    val checksum = BackupChecksum.calculate(provisional)
    return PraktikaBackupEnvelope(
        backupSchemaVersion = provisional.backupSchemaVersion,
        backupSequence = provisional.backupSequence,
        createdAtEpochMillis = provisional.createdAtEpochMillis,
        sourceAppVersionCode = provisional.sourceAppVersionCode,
        sourceAppVersionName = provisional.sourceAppVersionName,
        sourceSeedVersion = provisional.sourceSeedVersion,
        backupChecksumSha256 = checksum,
        payload = payload,
    )
}

internal fun validRead(
    slot: BackupSlotId,
    envelope: PraktikaBackupEnvelope,
): SlotReadResult.Valid = SlotReadResult.Valid(slot = slot, envelope = envelope)

internal fun invalidRead(slot: BackupSlotId): SlotReadResult.Invalid {
    return SlotReadResult.Invalid(
        slot = slot,
        reason = BackupFormatFailureReason.ChecksumMismatch,
    )
}

internal fun unreadableRead(slot: BackupSlotId): SlotReadResult.Unreadable {
    return SlotReadResult.Unreadable(slot = slot, exceptionClass = "java.io.IOException")
}

internal fun tooLargeRead(slot: BackupSlotId): SlotReadResult.TooLarge {
    return SlotReadResult.TooLarge(slot = slot)
}

internal fun ambiguousRead(slot: BackupSlotId, count: Int = 2): SlotReadResult.Ambiguous {
    return SlotReadResult.Ambiguous(slot = slot, matchCount = count)
}

internal fun coordinatorForTest(
    scope: TestScope,
    storage: RecordingBackupStorage,
    exportPayload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload =
        BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload,
    clock: CountingBackupClock = CountingBackupClock(),
    dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
): Pair<ProductionBackupCoordinator, CountingBackupClock> {
    val metadataFactory = BackupSnapshotMetadataFactory(
        clock = clock,
        appMetadataProvider = FixedBackupAppMetadataProvider(),
    )
    val coordinator = ProductionBackupCoordinator(
        storage = storage,
        exportAction = {
            BackupExportResult.Success(exportPayload)
        },
        metadataFactory = metadataFactory,
        scope = scope,
        ioDispatcher = dispatcher,
    )
    return coordinator to clock
}

internal suspend fun assertZeroWrites(
    storage: RecordingBackupStorage,
    outcome: BackupOutcome,
) {
    assertTrue(storage.writeCalls.isEmpty())
    assertTrue(storage.inspectionUnchanged())
    assertTrue(outcome !is BackupOutcome.Written)
}

internal suspend fun assertSingleWrite(
    storage: RecordingBackupStorage,
    expectedSlot: BackupSlotId,
    expectedSequence: Long,
    outcome: BackupOutcome,
    expectedCreatedAtEpochMillis: Long = 1_800_000_000_000L,
) {
    assertEquals(1, storage.writeCalls.size)
    assertEquals(expectedSlot, storage.writeCalls.single().slot)
    assertEquals(expectedSequence, storage.writeCalls.single().sequence)
    assertEquals(
        BackupOutcome.Written(
            slot = expectedSlot,
            sequence = expectedSequence,
            createdAtEpochMillis = expectedCreatedAtEpochMillis,
        ),
        outcome,
    )
}

internal fun runCoordinatorTest(
    block: suspend TestScope.(ProductionBackupCoordinator, RecordingBackupStorage, CountingBackupClock) -> Unit,
) = runTest {
    val storage = RecordingBackupStorage()
    val (coordinator, clock) = coordinatorForTest(this, storage)
    block(coordinator, storage, clock)
}

internal fun practiceStateWithNextCyclePosition(
    payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload,
    nextCyclePosition: Int,
): com.me4hik.praktika.data.backup.model.PraktikaBackupPayload {
    val state = payload.practiceState
    return com.me4hik.praktika.data.backup.model.PraktikaBackupPayload(
        practiceState = com.me4hik.praktika.data.backup.model.BackupPracticeState(
            isPracticeStarted = state.isPracticeStarted,
            isPaused = state.isPaused,
            practiceStartedAtEpochMillis = state.practiceStartedAtEpochMillis,
            currentCycleNumber = state.currentCycleNumber,
            nextCyclePosition = nextCyclePosition,
            lastProcessedAtEpochMillis = state.lastProcessedAtEpochMillis,
            pausedAtEpochMillis = state.pausedAtEpochMillis,
            activeZoneId = state.activeZoneId,
            seedVersion = state.seedVersion,
        ),
        scheduleSlots = payload.scheduleSlots,
        occurrences = payload.occurrences,
        answers = payload.answers,
    )
}

internal fun practiceStatePaused(
    payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload,
    pausedAtEpochMillis: Long,
): com.me4hik.praktika.data.backup.model.PraktikaBackupPayload {
    val state = payload.practiceState
    return com.me4hik.praktika.data.backup.model.PraktikaBackupPayload(
        practiceState = com.me4hik.praktika.data.backup.model.BackupPracticeState(
            isPracticeStarted = state.isPracticeStarted,
            isPaused = true,
            practiceStartedAtEpochMillis = state.practiceStartedAtEpochMillis,
            currentCycleNumber = state.currentCycleNumber,
            nextCyclePosition = state.nextCyclePosition,
            lastProcessedAtEpochMillis = state.lastProcessedAtEpochMillis,
            pausedAtEpochMillis = pausedAtEpochMillis,
            activeZoneId = state.activeZoneId,
            seedVersion = state.seedVersion,
        ),
        scheduleSlots = payload.scheduleSlots,
        occurrences = payload.occurrences,
        answers = payload.answers,
    )
}

internal fun payloadWithExtraAnswer(
    payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload,
    answer: com.me4hik.praktika.data.backup.model.BackupAnswer,
): com.me4hik.praktika.data.backup.model.PraktikaBackupPayload {
    return com.me4hik.praktika.data.backup.model.PraktikaBackupPayload(
        practiceState = payload.practiceState,
        scheduleSlots = payload.scheduleSlots,
        occurrences = payload.occurrences,
        answers = payload.answers + answer,
    )
}
