package com.me4hik.praktika.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataContext
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.restore.BackupRestoreDomainFailureReason
import com.me4hik.praktika.data.backup.restore.BackupRestoreDomainValidator
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.RoomBackupRestorer
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.mood.RoomMoodCheckInRepository
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MoodCheckInsBackupV3HostTest {
    private lateinit var sourceDatabase: PraktikaDatabase
    private lateinit var targetDatabase: PraktikaDatabase
    private lateinit var assetJson: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assetJson = checkNotNull(javaClass.classLoader)
            .getResourceAsStream("questions.json")?.bufferedReader()?.use { it.readText() }
            ?: error("questions.json missing from test resources")
        sourceDatabase = inMemory(context)
        targetDatabase = inMemory(context)
        seedFresh(sourceDatabase)
        seedFresh(targetDatabase)
    }

    @After
    fun tearDown() {
        if (::sourceDatabase.isInitialized && sourceDatabase.isOpen) sourceDatabase.close()
        if (::targetDatabase.isInitialized && targetDatabase.isOpen) targetDatabase.close()
    }

    @Test
    fun v3Roundtrip_singleMood_remapsOccurrenceId() = runBlocking {
        prepareAvailableOccurrence(sourceDatabase)
        val occurrence = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        val timeProvider = FakeTimeProvider(1_700_000_100_000L, BackupRestoreFixtures.ZONE_MOSCOW)
        RoomMoodCheckInRepository(sourceDatabase, timeProvider)
            .upsertForOccurrence(occurrence.id, MoodLevel.GOOD)

        val exported = exportPayload(sourceDatabase)
        assertEquals(1, exported.moodCheckIns.size)
        assertEquals(MoodLevel.GOOD.name, exported.moodCheckIns.single().level)

        val envelope = assembleV3(exported)
        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V3, envelope.backupSchemaVersion)
        assertEquals(BackupRestoreResult.Success, RoomBackupRestorer(targetDatabase).restore(envelope))

        val restoredOccurrence = targetDatabase.questionOccurrenceDao()
            .getByCycleAndPosition(1, 1)!!
        val mood = targetDatabase.moodCheckInDao().getByOccurrenceId(restoredOccurrence.id)!!
        assertEquals(restoredOccurrence.id, mood.occurrenceId)
        assertEquals(MoodLevel.GOOD, mood.level)
        assertEquals(restoredOccurrence.questionId, mood.questionId)
    }

    @Test
    fun v2EnvelopeWithMoodPayload_rejectedByDomainValidator() {
        val base = BackupRestoreFixtures.notStartedPayload()
        val payload = PraktikaBackupPayload(
            practiceState = base.practiceState,
            scheduleSlots = base.scheduleSlots,
            occurrences = base.occurrences,
            answers = base.answers,
            deferEvents = emptyList(),
            moodCheckIns = listOf(
                com.me4hik.praktika.data.backup.model.BackupMoodCheckIn(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    questionId = 1,
                    level = MoodLevel.NEUTRAL.name,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                    zoneId = "UTC",
                ),
            ),
        )
        val envelope = PraktikaBackupEnvelope(
            backupSchemaVersion = BackupConstants.BACKUP_SCHEMA_VERSION_V2,
            backupSequence = 1L,
            createdAtEpochMillis = 1_700_000_300_000L,
            sourceAppVersionCode = 7,
            sourceAppVersionName = "1.0",
            sourceSeedVersion = payload.practiceState.seedVersion,
            backupChecksumSha256 = "a".repeat(64),
            payload = payload,
        )
        val reason = BackupRestoreDomainValidator().validateEnvelope(envelope)
        assertEquals(
            BackupRestoreDomainFailureReason.MOOD_CHECK_INS_NOT_ALLOWED_FOR_SCHEMA,
            reason,
        )
    }

    private suspend fun prepareAvailableOccurrence(database: PraktikaDatabase) {
        val zoneId = BackupRestoreFixtures.ZONE_MOSCOW
        val startMillis = ZonedDateTime.of(2024, 6, 1, 11, 0, 0, 0, ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
        val timeProvider = FakeTimeProvider(startMillis, zoneId)
        val repository = CycleRepository(database, timeProvider, NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        assertEquals(CycleResult.PracticeStarted, repository.startPractice())
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered().single()
        timeProvider.setEpochMillis(incomplete.plannedAtEpochMillis)
        repository.syncEnvironmentAndReconcile()
        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            database.questionOccurrenceDao().getIncompleteOrdered().single().status,
        )
    }

    private suspend fun exportPayload(database: PraktikaDatabase): PraktikaBackupPayload {
        return when (val result = RoomBackupExporter(database).export()) {
            is BackupExportResult.Success -> result.payload
            else -> error("Export failed: $result")
        }
    }

    private fun assembleV3(payload: PraktikaBackupPayload): PraktikaBackupEnvelope {
        val assembled = BackupEnvelopeAssembler.assemble(
            payload = payload,
            backupSequence = BackupRestoreFixtures.BACKUP_SEQUENCE,
            metadata = BackupSnapshotMetadataContext(
                createdAtEpochMillis = BackupRestoreFixtures.CREATED_AT_EPOCH_MILLIS,
                sourceAppVersionCode = BackupRestoreFixtures.SOURCE_APP_VERSION_CODE,
                sourceAppVersionName = BackupRestoreFixtures.SOURCE_APP_VERSION_NAME,
            ),
        )
        check(assembled is com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult.Success) {
            "Assemble failed: $assembled"
        }
        return assembled.envelope
    }

    private suspend fun seedFresh(database: PraktikaDatabase) {
        val seedResult = DatabaseSeeder().seedFromJson(
            json = assetJson,
            database = database,
            activeZoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
        check(seedResult == SeedResult.Inserted || seedResult == SeedResult.AlreadyInitialized)
    }

    private fun inMemory(context: Context): PraktikaDatabase {
        return Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
