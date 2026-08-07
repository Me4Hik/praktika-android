// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - system event receiver tests
package com.me4hik.praktika.notification

import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.runtime.GrantedNotificationPermissionPolicy
import com.me4hik.praktika.runtime.NoOpRuntimeForegroundDriver
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeMode
import com.me4hik.praktika.runtime.TestPraktikaRuntimeBuilder
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemEventReceiverInstrumentedTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            PraktikaRuntimeHolder.resetForTests()
            database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
            val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            DatabaseSeeder().seedFromJson(assetJson, database, ZONE_KIEV)
            timeProvider = FakeTimeProvider(epochAt(8, 0), ZONE_KIEV)
            val runtime = TestPraktikaRuntimeBuilder.build(
                context = context,
                database = database,
                timeProvider = timeProvider,
                databaseName = "system-event-receiver-test",
                mode = RuntimeMode.PRODUCTION,
                foregroundDriver = NoOpRuntimeForegroundDriver(),
                soundPreferenceRepository = DataStoreSoundPreferenceRepository(context),
                permissionRepository = GrantedNotificationPermissionPolicy(),
            )
            runtime.cycleRepository.startPractice()
            PraktikaRuntimeHolder.setForTests(runtime)
        }
    }

    @After
    fun tearDown() {
        PraktikaRuntimeHolder.resetForTests()
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun bootCompletedSyncPreservesSingleIncompleteOccurrence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val beforeId = database.questionOccurrenceDao().getIncompleteOrdered().single().id
        SystemEventReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        delay(2_000)
        val after = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(beforeId, after.id)
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)
    }

    @Test
    fun timezoneChangedSyncUpdatesActiveZoneWithoutDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val beforeId = database.questionOccurrenceDao().getIncompleteOrdered().single().id
        timeProvider.setZoneId(ZONE_LONDON)
        SystemEventReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        delay(2_000)
        val after = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(beforeId, after.id)
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)
        assertEquals(ZONE_LONDON, database.practiceStateDao().get()!!.activeZoneId)
    }

    private fun epochAt(hour: Int, minute: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, 0, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
        const val ZONE_LONDON = "Europe/London"
    }
}
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
