// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room exporter reader host tests
package com.me4hik.praktika.ui.acceptance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataContext
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.RoomBackupRestorer
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedDataValidator
import com.me4hik.praktika.data.seed.SeedResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DeviceAcceptanceRoomStateReaderTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var restorer: RoomBackupRestorer
    private lateinit var exporter: RoomBackupExporter
    private lateinit var reader: ExporterDeviceAcceptanceRoomStateReader

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        val seedResult = DatabaseSeeder().seedFromJson(
            json = assetJson,
            database = database,
            activeZoneId = "Europe/Moscow",
        )
        check(seedResult == SeedResult.Inserted || seedResult == SeedResult.AlreadyInitialized)
        restorer = RoomBackupRestorer(database)
        exporter = RoomBackupExporter(database)
        reader = ExporterDeviceAcceptanceRoomStateReader(exporter)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun freshSeededState_reportsNotStartedZeros() = runBlocking {
        val result = reader.read()
        assertTrue(result is DeviceAcceptanceRoomReadResult.Ok)
        val summary = (result as DeviceAcceptanceRoomReadResult.Ok).summary
        assertFalse(summary.practiceStarted)
        assertEquals(0, summary.occurrenceCount)
        assertEquals(0, summary.answerCount)
        assertEquals(0, summary.deletedTextCount)
        assertEquals(0, summary.answeredCount)
        assertEquals(0, summary.skippedCount)
        assertEquals(0, summary.scheduledCount)
        assertEquals(0, summary.missedCount)
        assertEquals(0, summary.availableCount)
        assertEquals(SeedDataValidator.EXPECTED_SLOT_COUNT, summary.scheduleMinutes.size)
    }

    @Test
    fun restoredRichPausedFixture_exactCounts_andNoRoomMutation() = runBlocking {
        val payload = richPausedPayload()
        val envelope = assemble(payload)
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))

        val beforeExport = exporter.export()
        assertTrue(beforeExport is BackupExportResult.Success)
        val beforePayload = (beforeExport as BackupExportResult.Success).payload

        val result = reader.read()
        assertTrue(result is DeviceAcceptanceRoomReadResult.Ok)
        val summary = (result as DeviceAcceptanceRoomReadResult.Ok).summary
        assertTrue(summary.practiceStarted)
        assertTrue(summary.isPaused)
        assertEquals(1, summary.currentCycleNumber)
        assertEquals(4, summary.nextCyclePosition)
        assertEquals(listOf(900, 660, 1140), summary.scheduleMinutes)
        assertEquals(4, summary.occurrenceCount)
        assertEquals(1, summary.answerCount)
        assertEquals(1, summary.deletedTextCount)
        assertEquals(2, summary.answeredCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(1, summary.scheduledCount)
        assertEquals(0, summary.missedCount)

        val afterExport = exporter.export()
        assertTrue(afterExport is BackupExportResult.Success)
        assertEquals(beforePayload, (afterExport as BackupExportResult.Success).payload)
    }

    @Test
    fun exporterParity_roomSummaryOverlapsBackupSemantics() = runBlocking {
        assertEquals(BackupRestoreResult.Success, restorer.restore(assemble(richPausedPayload())))
        val export = exporter.export() as BackupExportResult.Success
        val room = DeviceAcceptanceRoomStateSummaryFactory.fromPayload(export.payload)
        val backup = DeviceAcceptanceSemanticSummaryFactory.fromEnvelope(assemble(export.payload))
        assertTrue(DeviceAcceptanceRoomStateSummaryFactory.overlapsBackupSemantics(room, backup))
    }

    private fun richPausedPayload(): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = true,
                isPaused = true,
                practiceStartedAtEpochMillis = 1_700_000_000_000L,
                currentCycleNumber = 1,
                nextCyclePosition = 4,
                lastProcessedAtEpochMillis = 1_700_000_050_000L,
                pausedAtEpochMillis = 1_700_000_100_000L,
                activeZoneId = "Europe/Moscow",
                seedVersion = SeedDataValidator.EXPECTED_SEED_VERSION,
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot(1, 900),
                BackupScheduleSlot(2, 660),
                BackupScheduleSlot(3, 1140),
            ),
            occurrences = listOf(
                occ(1, QuestionOccurrenceStatus.ANSWERED),
                occ(2, QuestionOccurrenceStatus.ANSWERED),
                occ(3, QuestionOccurrenceStatus.SKIPPED_BY_USER),
                occ(4, QuestionOccurrenceStatus.SCHEDULED),
            ),
            answers = listOf(BackupAnswer(1, 1, "kept-answer", 10L)),
        )
    }

    private fun occ(position: Int, status: QuestionOccurrenceStatus): BackupOccurrence {
        return BackupOccurrence(
            questionId = position,
            questionTextSnapshot = "q$position",
            cycleNumber = 1,
            cyclePosition = position,
            scheduleSlotIndex = ((position - 1) % 3) + 1,
            plannedAtEpochMillis = 1_700_000_000_000L + position,
            availableUntilEpochMillis = 1_700_000_100_000L + position,
            openedAtEpochMillis = null,
            completedAtEpochMillis = if (status == QuestionOccurrenceStatus.SCHEDULED) null else 1L,
            status = status.name,
            zoneId = "Europe/Moscow",
        )
    }

    private fun assemble(payload: PraktikaBackupPayload) =
        when (
            val result = BackupEnvelopeAssembler.assemble(
                payload = payload,
                backupSequence = 99L,
                metadata = BackupSnapshotMetadataContext(
                    createdAtEpochMillis = 1_700_000_300_000L,
                    sourceAppVersionCode = 7,
                    sourceAppVersionName = "1.0-accelerated",
                ),
            )
        ) {
            is BackupEnvelopeAssemblyResult.Success -> result.envelope
            else -> error("assemble failed: $result")
        }
}
// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
