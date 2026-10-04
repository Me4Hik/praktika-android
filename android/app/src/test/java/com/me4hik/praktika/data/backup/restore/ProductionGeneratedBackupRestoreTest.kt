// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik START - production-generated backup restore
package com.me4hik.praktika.data.backup.restore

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ProductionGeneratedBackupRestoreTest {
    private lateinit var sourceDatabase: com.me4hik.praktika.data.local.PraktikaDatabase
    private lateinit var targetDatabase: com.me4hik.praktika.data.local.PraktikaDatabase
    private lateinit var assetJson: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assetJson = checkNotNull(javaClass.classLoader)
            .getResourceAsStream("questions.json")?.bufferedReader()?.use { it.readText() }
            ?: error("questions.json missing from test resources")

        sourceDatabase = inMemoryDatabase(context)
        targetDatabase = inMemoryDatabase(context)
        seedFresh(sourceDatabase)
        seedFresh(targetDatabase)
    }

    @After
    fun tearDown() {
        if (::sourceDatabase.isInitialized && sourceDatabase.isOpen) {
            sourceDatabase.close()
        }
        if (::targetDatabase.isInitialized && targetDatabase.isOpen) {
            targetDatabase.close()
        }
    }

    @Test
    fun productionGeneratedEnvelope_restoreReconcileAndReadSnapshot_succeed() = runBlocking {
        val zoneId = BackupRestoreFixtures.ZONE_MOSCOW
        val startMillis = ZonedDateTime.of(2026, 8, 4, 11, 0, 0, 0, ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
        val timeProvider = FakeTimeProvider(startMillis, zoneId)
        val sourceRepository = CycleRepository(sourceDatabase, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())

        assertEquals(CycleResult.PracticeStarted, sourceRepository.startPractice())

        val incomplete = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        timeProvider.setEpochMillis(incomplete.plannedAtEpochMillis)
        sourceRepository.syncEnvironmentAndReconcile()

        val available = sourceDatabase.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(
            CycleResult.AnswerSaved,
            sourceRepository.saveAnswer(
                expectedOccurrenceId = available.id,
                answerText = "Production generated answer",
            ),
        )

        val sourceExporter = RoomBackupExporter(sourceDatabase)
        val exported = when (val result = sourceExporter.export()) {
            is BackupExportResult.Success -> result.payload
            else -> error("Source export failed: $result")
        }

        val restorer = RoomBackupRestorer(targetDatabase)
        val envelope = BackupRestoreFixtures.envelope(exported)
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))

        when (val roundtrip = RoomBackupExporter(targetDatabase).export()) {
            is BackupExportResult.Success -> {
                assertTrue(
                    BackupPayloadSemanticComparator.equalsSemantically(
                        exported,
                        roundtrip.payload,
                    ),
                )
            }
            else -> error("Target export failed: $roundtrip")
        }

        val targetTimeProvider = FakeTimeProvider(startMillis, zoneId)
        val targetRepository = CycleRepository(targetDatabase, targetTimeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        val readRepository = RoomPracticeReadRepository(targetDatabase)

        val reconcileResult = targetRepository.syncEnvironmentAndReconcile()
        assertTrue(reconcileResult is CycleResult.ReconcileNoChanges || reconcileResult is CycleResult.ReconcileChanged)

        val snapshot = readRepository.observeSnapshot().first()
        assertTrue(snapshot.practiceState.isPracticeStarted)
        assertNotNull(snapshot.incompleteOccurrence)
    }

    private suspend fun seedFresh(database: com.me4hik.praktika.data.local.PraktikaDatabase) {
        val seedResult = DatabaseSeeder().seedFromJson(
            json = assetJson,
            database = database,
            activeZoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
        check(seedResult == SeedResult.Inserted || seedResult == SeedResult.AlreadyInitialized) {
            "Unexpected seed result: $seedResult"
        }
    }

    private fun inMemoryDatabase(context: Context): com.me4hik.praktika.data.local.PraktikaDatabase {
        return Room.inMemoryDatabaseBuilder(context, com.me4hik.praktika.data.local.PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik END
