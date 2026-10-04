// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - isolated accelerated UI test runtime
package com.me4hik.praktika.ui.practice

import android.content.Context
import androidx.room.Room
import com.me4hik.praktika.accelerated.AcceleratedClockState
import com.me4hik.praktika.accelerated.AcceleratedCycleDriver
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.accelerated.FakeMonotonicTimeSource
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.RuntimeMode
import com.me4hik.praktika.runtime.TestPraktikaRuntimeBuilder
import java.time.ZoneId
import java.time.ZonedDateTime

class AcceleratedUiTestHarness(
    private val context: Context,
) {
    lateinit var database: PraktikaDatabase
        private set
    lateinit var runtime: PraktikaRuntime
        private set
    lateinit var timeProvider: AcceleratedTimeProvider
        private set
    lateinit var foregroundDriver: AcceleratedCycleDriver
        private set

    suspend fun setUp(initialHour: Int = 8, initialMinute: Int = 0) {
        clearAcceleratedClockFile()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PraktikaDatabase::class.java,
        ).build()
        val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        DatabaseSeeder().seedFromJson(assetJson, database, TEST_ZONE_ID)
        val initialEpoch = epochAt(initialHour, initialMinute, 0)
        val monotonic = FakeMonotonicTimeSource(
            elapsedRealtimeMillis = 0L,
            wallClockEpochMillis = initialEpoch,
            bootCount = 1,
            zoneId = TEST_ZONE_ID,
        )
        val storage = AcceleratedTimeStorage(context)
        val initialState = AcceleratedClockState.createInitial(
            zoneId = TEST_ZONE_ID,
            virtualStartEpochMillis = initialEpoch,
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 0L,
        )
        timeProvider = AcceleratedTimeProvider(initialState, storage, monotonic)
        val cycleRepository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        foregroundDriver = AcceleratedCycleDriver(cycleRepository, timeProvider)
        val soundPreferenceRepository = DataStoreSoundPreferenceRepository(context)
        runtime = TestPraktikaRuntimeBuilder.build(
            context = context,
            database = database,
            timeProvider = timeProvider,
            databaseName = IN_MEMORY_DATABASE_LABEL,
            mode = RuntimeMode.ACCELERATED,
            foregroundDriver = foregroundDriver,
            soundPreferenceRepository = soundPreferenceRepository,
        )
    }

    suspend fun startPractice() {
        runtime.cycleRepository.startPractice()
    }

    suspend fun advanceCurrentOccurrenceToAvailable() {
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val target = occurrence.plannedAtEpochMillis
        val currentVirtual = timeProvider.currentVirtualNow()
        if (target > currentVirtual) {
            timeProvider.advanceToVirtualEpochMillis(target)
        }
        runtime.cycleRepository.reconcile()
        timeProvider.checkpoint()
    }

    fun tearDown() {
        clearAcceleratedClockFile()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    private fun clearAcceleratedClockFile() {
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, ZoneId.of(TEST_ZONE_ID))
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        private const val TEST_ZONE_ID = "Europe/Kiev"
        const val IN_MEMORY_DATABASE_LABEL = "in-memory-accelerated-ui-test"
    }
}
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
