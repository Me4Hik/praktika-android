// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - isolated production UI test runtime
package com.me4hik.praktika.ui.practice

import android.content.Context
import androidx.room.Room
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.runtime.NoOpRuntimeForegroundDriver
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.RuntimeMode
import com.me4hik.praktika.runtime.TestPraktikaRuntimeBuilder
import java.time.ZoneId
import java.time.ZonedDateTime

class PracticeUiTestHarness(
    private val context: Context,
) {
    lateinit var database: PraktikaDatabase
        private set
    lateinit var runtime: PraktikaRuntime
        private set
    private lateinit var timeProvider: FakeTimeProvider

    val usesProductionDatabaseFile: Boolean
        get() = false

    suspend fun setUp(initialHour: Int = 8, initialMinute: Int = 0) {
        database = Room.inMemoryDatabaseBuilder(
            context,
            PraktikaDatabase::class.java,
        ).build()
        val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        DatabaseSeeder().seedFromJson(assetJson, database, TEST_ZONE_ID)
        timeProvider = FakeTimeProvider(epochAt(initialHour, initialMinute, 0), TEST_ZONE_ID)
        runtime = buildRuntime()
    }

    suspend fun startPractice() {
        runtime.cycleRepository.startPractice()
    }

    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    private fun buildRuntime(): PraktikaRuntime {
        val soundPreferenceRepository = DataStoreSoundPreferenceRepository(context)
        return TestPraktikaRuntimeBuilder.build(
            context = context,
            database = database,
            timeProvider = timeProvider,
            databaseName = IN_MEMORY_DATABASE_LABEL,
            mode = RuntimeMode.PRODUCTION,
            foregroundDriver = NoOpRuntimeForegroundDriver(),
            soundPreferenceRepository = soundPreferenceRepository,
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, ZoneId.of(TEST_ZONE_ID))
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        private const val TEST_ZONE_ID = "Europe/Kiev"
        const val IN_MEMORY_DATABASE_LABEL = "in-memory-production-ui-test"
        const val PRODUCTION_DATABASE_NAME = "praktika.db"
    }
}
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
