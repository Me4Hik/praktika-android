// 10.08.2026 Post-release fixes cursor by Me4Hik START - foreground sync loop regression
package com.me4hik.praktika.runtime

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionForegroundSyncLoopRegressionTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var foregroundDriver: ProductionForegroundDriver
    private lateinit var eventsFile: File

    @Before
    fun setUp() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
            val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            DatabaseSeeder().seedFromJson(assetJson, database, ZONE_KIEV)
            timeProvider = FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV)
            eventsFile = File(context.cacheDir, "foreground-sync-loop-events.jsonl")
            DiagnosticsRecorder.installForTests(eventsFile)
            val runtime = TestPraktikaRuntimeBuilder.build(
                context = context,
                database = database,
                timeProvider = timeProvider,
                databaseName = "production-foreground-sync-loop-test",
                mode = RuntimeMode.PRODUCTION,
                foregroundDriver = NoOpRuntimeForegroundDriver(),
                soundPreferenceRepository = DataStoreSoundPreferenceRepository(context),
                permissionRepository = GrantedNotificationPermissionPolicy(),
            )
            foregroundDriver = ProductionForegroundDriver(
                coordinator = runtime.notificationCoordinator,
                practiceReadRepository = runtime.practiceReadRepository,
                nowEpochMillis = timeProvider::nowEpochMillis,
            )
            runtime.cycleRepository.startPractice()
        }
    }

    @After
    fun tearDown() {
        DiagnosticsRecorder.resetForTests()
        eventsFile.delete()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun availableWaitingDoesNotCreateForegroundSyncStorm() = runBlocking {
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)

        val practiceStateBefore = database.practiceStateDao().get()!!
        val driverJob = async {
            foregroundDriver.runWhileForeground()
        }

        delay(5_000)

        driverJob.cancel()
        driverJob.join()

        val practiceStateAfter = database.practiceStateDao().get()!!
        assertEquals(practiceStateBefore, practiceStateAfter)

        val events = DiagnosticsRecorder.get().getRecentEvents(limit = 500)
        val foregroundStarted = events.count {
            it.category == DiagnosticCategory.NOTIFICATION &&
                it.name == "notification_sync_started" &&
                it.metadata["reason"] == "FOREGROUND"
        }
        val foregroundResults = events.count {
            it.category == DiagnosticCategory.NOTIFICATION &&
                it.name == "notification_sync_result" &&
                it.metadata["reason"] == "FOREGROUND"
        }

        assertTrue(
            "Expected bounded FOREGROUND sync starts, got $foregroundStarted",
            foregroundStarted <= 3,
        )
        assertTrue(
            "Expected bounded FOREGROUND sync results, got $foregroundResults",
            foregroundResults <= 3,
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int = 0): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
