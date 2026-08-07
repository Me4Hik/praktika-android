// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - production foreground boundary driver
package com.me4hik.praktika.runtime

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.seed.DatabaseSeeder
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionForegroundDriverInstrumentedTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var foregroundDriver: ProductionForegroundDriver

    @Before
    fun setUp() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
            val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            DatabaseSeeder().seedFromJson(assetJson, database, ZONE_KIEV)
            timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
            val runtime = TestPraktikaRuntimeBuilder.build(
                context = context,
                database = database,
                timeProvider = timeProvider,
                databaseName = "production-foreground-driver-test",
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
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun scheduledToAvailableToMissedWithoutManualCoordinatorCalls() = runBlocking {
        val original = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, original.status)

        timeProvider.setEpochMillis(original.plannedAtEpochMillis - 500L)
        val driverJob = async {
            foregroundDriver.runWhileForeground()
        }

        delay(100)
        timeProvider.setEpochMillis(original.plannedAtEpochMillis + 1L)
        delay(600)

        val available = database.questionOccurrenceDao().getById(original.id)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, available.status)

        driverJob.cancel()
        driverJob.join()

        timeProvider.setEpochMillis(available.availableUntilEpochMillis - 500L)
        val missedDriverJob = async {
            foregroundDriver.runWhileForeground()
        }
        delay(100)
        timeProvider.setEpochMillis(available.availableUntilEpochMillis + 1L)
        delay(600)

        assertEquals(
            QuestionOccurrenceStatus.MISSED_BY_TIME,
            database.questionOccurrenceDao().getById(original.id)!!.status,
        )
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertNotEquals(original.id, incomplete.id)
        assertTrue(
            incomplete.status == QuestionOccurrenceStatus.SCHEDULED ||
                incomplete.status == QuestionOccurrenceStatus.AVAILABLE,
        )
        missedDriverJob.cancel()
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
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
