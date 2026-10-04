package com.me4hik.praktika.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.codec.BackupJsonDecoder
import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.integrity.BackupIntegrityEncoderV1
import com.me4hik.praktika.data.backup.integrity.BackupIntegrityEncoderV2
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataContext
import com.me4hik.praktika.data.backup.model.BackupDeferEvent
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.restore.BackupPayloadSemanticComparator
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.RoomBackupRestorer
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator
import com.me4hik.praktika.data.backup.validate.BackupJsonDecodeResult
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DeferEventsBackupV2HostTest {
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
    fun v1GoldenIntegrity_unchanged() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V1, envelope.backupSchemaVersion)
        assertEquals(BackupGoldenFixtures.GOLDEN_INTEGRITY_HASH_V1, BackupChecksum.calculate(envelope))
        val bytes = BackupIntegrityEncoderV1.encode(envelope)
        assertEquals(bytes.toList(), BackupIntegrityEncoderV1.encode(envelope).toList())
    }

    @Test
    fun v2Roundtrip_singleDefer_remapsOccurrenceId() = runBlocking {
        prepareAvailableOccurrence(sourceDatabase)
        val occurrenceBefore = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        val beforeId = occurrenceBefore.id
        deferOnce(sourceDatabase, occurrenceBefore.id, durationMinutes = 15, atHour = 11, atMinute = 20)

        val exported = exportPayload(sourceDatabase)
        assertEquals(1, exported.deferEvents.size)
        assertEquals(1, exported.deferEvents.single().cyclePosition)

        val envelope = assembleV2(exported)
        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V3, envelope.backupSchemaVersion)
        assertEquals(BackupRestoreResult.Success, RoomBackupRestorer(targetDatabase).restore(envelope))

        val restoredOccurrence = targetDatabase.questionOccurrenceDao()
            .getByCycleAndPosition(1, 1)!!
        val events = targetDatabase.deferEventDao().getForOccurrenceOrdered(restoredOccurrence.id)
        assertEquals(1, events.size)
        assertEquals(restoredOccurrence.id, events.single().occurrenceId)
        assertEquals(15, events.single().durationMinutes)
        assertEquals(occurrenceBefore.questionId, events.single().questionId)
        // Portable key survived remap (Room ids may coincidentally match across DBs).
        assertEquals(1, exported.deferEvents.single().cycleNumber)
        assertEquals(1, exported.deferEvents.single().cyclePosition)
        assertEquals(beforeId, occurrenceBefore.id)

        val roundtrip = exportPayload(targetDatabase)
        assertTrue(BackupPayloadSemanticComparator.equalsSemantically(exported, roundtrip))
    }

    @Test
    fun v2Roundtrip_multipleDefersSameOccurrence() = runBlocking {
        prepareAvailableOccurrence(sourceDatabase)
        val occurrence = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        deferOnce(sourceDatabase, occurrence.id, durationMinutes = 15, atHour = 11, atMinute = 10)
        deferOnce(sourceDatabase, occurrence.id, durationMinutes = 5, atHour = 11, atMinute = 12)

        val exported = exportPayload(sourceDatabase)
        assertEquals(2, exported.deferEvents.size)
        assertEquals(listOf(15, 5), exported.deferEvents.map { it.durationMinutes })

        val envelope = assembleV2(exported)
        assertEquals(BackupRestoreResult.Success, RoomBackupRestorer(targetDatabase).restore(envelope))

        val restored = targetDatabase.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val events = targetDatabase.deferEventDao().getForOccurrenceOrdered(restored.id)
        assertEquals(2, events.size)
        assertEquals(listOf(15, 5), events.map { it.durationMinutes })
        assertTrue(events.all { it.occurrenceId == restored.id })

        assertTrue(
            BackupPayloadSemanticComparator.equalsSemantically(
                exported,
                exportPayload(targetDatabase),
            ),
        )
    }

    @Test
    fun v1LegacyRestore_leavesDeferEventsEmpty() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V1, envelope.backupSchemaVersion)
        assertTrue(envelope.payload.deferEvents.isEmpty())

        assertEquals(BackupRestoreResult.Success, RoomBackupRestorer(targetDatabase).restore(envelope))
        assertEquals(0, targetDatabase.deferEventDao().count())
    }

    @Test
    fun v1Decode_withoutDeferEventsKey_yieldsEmptyList() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        val decoded = BackupJsonDecoder.decode(bytes)
        assertTrue(decoded is BackupJsonDecodeResult.Success)
        val success = decoded as BackupJsonDecodeResult.Success
        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V1, success.envelope.backupSchemaVersion)
        assertTrue(success.envelope.payload.deferEvents.isEmpty())
        assertEquals(
            BackupFormatValidationResult.Valid::class,
            BackupFormatValidator.validate(success.envelope)::class,
        )
    }

    @Test
    fun tamperedV2DeferEvent_failsChecksum() = runBlocking {
        prepareAvailableOccurrence(sourceDatabase)
        val occurrence = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        deferOnce(sourceDatabase, occurrence.id, durationMinutes = 10, atHour = 11, atMinute = 15)

        val envelope = assembleV2(exportPayload(sourceDatabase))
        val tamperedPayload = PraktikaBackupPayload(
            practiceState = envelope.payload.practiceState,
            scheduleSlots = envelope.payload.scheduleSlots,
            occurrences = envelope.payload.occurrences,
            answers = envelope.payload.answers,
            deferEvents = listOf(
                envelope.payload.deferEvents.single().let { event ->
                    BackupDeferEvent(
                        cycleNumber = event.cycleNumber,
                        cyclePosition = event.cyclePosition,
                        questionId = event.questionId,
                        occurredAtEpochMillis = event.occurredAtEpochMillis,
                        deferredUntilEpochMillis = event.deferredUntilEpochMillis,
                        durationMinutes = 30,
                        zoneId = event.zoneId,
                    )
                },
            ),
        )
        val tampered = PraktikaBackupEnvelope(
            backupSchemaVersion = envelope.backupSchemaVersion,
            backupSequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceAppVersionCode = envelope.sourceAppVersionCode,
            sourceAppVersionName = envelope.sourceAppVersionName,
            sourceSeedVersion = envelope.sourceSeedVersion,
            backupChecksumSha256 = envelope.backupChecksumSha256,
            payload = tamperedPayload,
        )
        val result = BackupFormatValidator.validate(tampered)
        assertTrue(result is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.ChecksumMismatch,
            (result as BackupFormatValidationResult.Invalid).reason,
        )
    }

    @Test
    fun targetNotEmpty_afterV2RestoreWithDefers() = runBlocking {
        prepareAvailableOccurrence(sourceDatabase)
        val occurrence = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        deferOnce(sourceDatabase, occurrence.id, durationMinutes = 15, atHour = 11, atMinute = 20)

        val envelope = assembleV2(exportPayload(sourceDatabase))
        assertEquals(BackupRestoreResult.Success, RoomBackupRestorer(targetDatabase).restore(envelope))
        assertTrue(targetDatabase.deferEventDao().count() > 0)

        val second = RoomBackupRestorer(targetDatabase).restore(envelope)
        assertEquals(BackupRestoreResult.TargetNotEmpty, second)
    }

    @Test
    fun v2Encoder_includesDeferEventsInDeterministicOrder() {
        val base = BackupRestoreFixtures.richPayload()
        val events = listOf(
            BackupDeferEvent(1, 3, 3, 300L, 400L, 30, BackupRestoreFixtures.ZONE_MOSCOW),
            BackupDeferEvent(1, 1, 1, 100L, 200L, 15, BackupRestoreFixtures.ZONE_MOSCOW),
            BackupDeferEvent(1, 1, 1, 150L, 250L, 5, BackupRestoreFixtures.ZONE_MOSCOW),
        )
        val payload = PraktikaBackupPayload(
            practiceState = base.practiceState,
            scheduleSlots = base.scheduleSlots,
            occurrences = base.occurrences,
            answers = base.answers,
            deferEvents = events,
        )
        val envelope = provisionalV2(payload)
        val first = BackupIntegrityEncoderV2.encode(envelope)
        val reordered = provisionalV2(
            PraktikaBackupPayload(
                practiceState = payload.practiceState,
                scheduleSlots = payload.scheduleSlots,
                occurrences = payload.occurrences,
                answers = payload.answers,
                deferEvents = events.reversed(),
            ),
        )
        assertEquals(first.toList(), BackupIntegrityEncoderV2.encode(reordered).toList())
    }

    private suspend fun prepareAvailableOccurrence(database: PraktikaDatabase) {
        val zoneId = BackupRestoreFixtures.ZONE_MOSCOW
        val startMillis = ZonedDateTime.of(2024, 6, 1, 11, 0, 0, 0, ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
        val timeProvider = FakeTimeProvider(startMillis, zoneId)
        val repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        assertEquals(CycleResult.PracticeStarted, repository.startPractice())
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered().single()
        timeProvider.setEpochMillis(incomplete.plannedAtEpochMillis)
        repository.syncEnvironmentAndReconcile()
        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            database.questionOccurrenceDao().getIncompleteOrdered().single().status,
        )
    }

    private suspend fun deferOnce(
        database: PraktikaDatabase,
        occurrenceId: Long,
        durationMinutes: Int,
        atHour: Int,
        atMinute: Int,
    ) {
        val zoneId = BackupRestoreFixtures.ZONE_MOSCOW
        val now = ZonedDateTime.of(2024, 6, 1, atHour, atMinute, 0, 0, ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
        val timeProvider = FakeTimeProvider(now, zoneId)
        val repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        val result = repository.deferAvailableOccurrence(occurrenceId, durationMinutes)
        assertTrue(result is CycleResult.DeferCompleted)
    }

    private suspend fun exportPayload(database: PraktikaDatabase): PraktikaBackupPayload {
        return when (val result = RoomBackupExporter(database).export()) {
            is BackupExportResult.Success -> result.payload
            else -> error("Export failed: $result")
        }
    }

    private fun assembleV2(payload: PraktikaBackupPayload): PraktikaBackupEnvelope {
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

    private fun provisionalV2(payload: PraktikaBackupPayload): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = BackupConstants.BACKUP_SCHEMA_VERSION_V2,
            backupSequence = 1L,
            createdAtEpochMillis = 1_700_000_300_000L,
            sourceAppVersionCode = 7,
            sourceAppVersionName = "1.0",
            sourceSeedVersion = payload.practiceState.seedVersion,
            backupChecksumSha256 = "",
            payload = payload,
        )
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
