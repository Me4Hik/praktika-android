// 10.08.2026 Post-release fixes cursor by Me4Hik START - coordinator Main-thread Room regression
package com.me4hik.praktika.data.backup.restore

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BackupRestoreCoordinatorMainThreadTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var cycleRepository: CycleRepository
    private lateinit var storage: FakeBackupStorage

    @Before
    fun setUp() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
                .build()
            val assetJson = checkNotNull(javaClass.classLoader)
                .getResourceAsStream("questions.json")?.bufferedReader()?.use { it.readText() }
                ?: error("questions.json missing from test resources")
            val seedResult = DatabaseSeeder().seedFromJson(
                json = assetJson,
                database = database,
                activeZoneId = BackupRestoreFixtures.ZONE_MOSCOW,
            )
            check(seedResult == SeedResult.Inserted || seedResult == SeedResult.AlreadyInitialized) {
                "Unexpected seed result: $seedResult"
            }
            cycleRepository = CycleRepository(
                database = database,
                timeProvider = FakeTimeProvider(
                    epochMillis = BackupRestoreFixtures.acceptanceReconcileNowMillis(),
                    zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
                ),
                backupMutationRequestSink = com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink,
            )
            storage = FakeBackupStorage(
                slotB = SlotReadResult.Valid(
                    slot = BackupSlotId.B,
                    envelope = BackupRestoreFixtures.richEnvelope().withSequence(2L),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun executeRestore_fromAndroidMainThread_dispatchesRuntimeSyncOffMain() = runBlocking {
        val exporter = RoomBackupExporter(database)
        val verifier = object : PostRestoreDataVerifier {
            override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
                return when (val exportResult = exporter.export()) {
                    is com.me4hik.praktika.data.backup.export.BackupExportResult.Success -> {
                        if (!BackupPayloadSemanticComparator.equalsSemantically(
                                restoredEnvelope.payload,
                                exportResult.payload,
                            )
                        ) {
                            PostRestoreVerificationResult.Mismatch("payload_semantic_mismatch")
                        } else {
                            PostRestoreVerificationResult.Success(
                                evidence = PostRestoreVerificationEvidence.fromPayload(exportResult.payload),
                            )
                        }
                    }

                    else -> PostRestoreVerificationResult.Mismatch("export_failed")
                }
            }
        }
        var notificationInvoked = false
        val coordinator = BackupRestoreCoordinator(
            database = database,
            restorer = RoomBackupRestorer(database),
            cycleRepository = cycleRepository,
            postRestoreVerifier = verifier,
            reconcileAction = { cycleRepository.syncEnvironmentAndReconcile() },
            notificationSyncAction = {
                notificationInvoked = true
                NotificationSyncObservation.Invoked
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val resultDeferred = async(Dispatchers.Main) {
            coordinator.executeRestore(storage, preview.identity, preview.preview)
        }
        while (resultDeferred.isActive) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(1)
        }
        val result = resultDeferred.await()

        assertTrue(result is BackupRestoreCoordinatorResult.FullSuccess)
        val success = result as BackupRestoreCoordinatorResult.FullSuccess
        assertTrue(success.verification is PostRestoreVerificationResult.Success)
        val evidence = (success.verification as PostRestoreVerificationResult.Success).evidence!!
        assertEquals(4, evidence.occurrenceCount)
        assertEquals(1, evidence.answerCount)
        assertEquals(1, evidence.deletedTextCount)
        assertEquals(2, evidence.answeredCount)
        assertEquals(1, evidence.skippedCount)
        assertEquals(1, evidence.scheduledCount)
        assertEquals(0, evidence.missedCount)
        assertEquals(1, evidence.currentCycleNumber)
        assertEquals(4, evidence.nextCyclePosition)
        assertTrue(
            success.reconcileResult is CycleResult.ReconcileNoChanges ||
                success.reconcileResult is CycleResult.ReconcileChanged,
        )
        assertTrue(notificationInvoked)
        assertTrue(database.practiceStateDao().get()!!.isPracticeStarted)
    }

    @Test
    fun executeRestore_fromAndroidMainThread_realReconcileDoesNotUseMainLooper() = runBlocking {
        val mainLooper = android.os.Looper.getMainLooper()
        var reconcileOnMain = false
        val coordinator = BackupRestoreCoordinator(
            database = database,
            restorer = RoomBackupRestorer(database),
            cycleRepository = cycleRepository,
            postRestoreVerifier = NoOpPostRestoreDataVerifier,
            reconcileAction = {
                reconcileOnMain = android.os.Looper.myLooper() == mainLooper
                cycleRepository.syncEnvironmentAndReconcile()
            },
            notificationSyncAction = { NotificationSyncObservation.Invoked },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val resultDeferred = async(Dispatchers.Main) {
            coordinator.executeRestore(storage, preview.identity, preview.preview)
        }
        while (resultDeferred.isActive) {
            Shadows.shadowOf(mainLooper).idle()
            delay(1)
        }
        resultDeferred.await()

        assertFalse(reconcileOnMain)
    }
}

private fun PraktikaBackupEnvelope.withSequence(sequence: Long): PraktikaBackupEnvelope {
    return PraktikaBackupEnvelope(
        backupSchemaVersion = backupSchemaVersion,
        backupSequence = sequence,
        createdAtEpochMillis = createdAtEpochMillis,
        sourceAppVersionCode = sourceAppVersionCode,
        sourceAppVersionName = sourceAppVersionName,
        sourceSeedVersion = sourceSeedVersion,
        backupChecksumSha256 = backupChecksumSha256,
        payload = payload,
    )
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
