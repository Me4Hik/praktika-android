// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - channel integration connected test
package com.me4hik.praktika.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.runtime.GrantedNotificationPermissionPolicy
import com.me4hik.praktika.runtime.NoOpRuntimeForegroundDriver
import com.me4hik.praktika.runtime.RuntimeMode
import com.me4hik.praktika.runtime.TestPraktikaRuntimeBuilder
import java.io.FileInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPlatformIntegrationProductionInstrumentedTest {
    @Test
    fun channelsCreatedWithExpectedSoundConfiguration() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val presenter = AndroidPracticeNotificationPresenter(context)
        presenter.ensureChannelsCreated()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundChannel = manager.getNotificationChannel(PracticeNotificationChannels.SOUND)
        val silentChannel = manager.getNotificationChannel(PracticeNotificationChannels.SILENT)
        assertNotNull(soundChannel)
        assertNotNull(silentChannel)
        assertNotNull(soundChannel?.sound)
        assertNull(silentChannel?.sound)
    }

    // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik START - PendingIntent flag regression
    @Test
    fun alarmSchedulerSchedulesReschedulesAndCancelsWithoutPendingIntentFlagException() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = AndroidAlarmScheduler(context)
        val triggerAt = System.currentTimeMillis() + 120_000L
        val plannedAlarm = BoundaryAlarmPlan(
            occurrenceId = 42L,
            eventType = BoundaryEventType.PLANNED_BOUNDARY,
            triggerAtEpochMillis = triggerAt,
            plannedAtEpochMillis = triggerAt,
        )
        val expiryAlarm = BoundaryAlarmPlan(
            occurrenceId = 42L,
            eventType = BoundaryEventType.EXPIRY_BOUNDARY,
            triggerAtEpochMillis = triggerAt + 3_600_000L,
            plannedAtEpochMillis = triggerAt,
        )
        val plan = NotificationPlan(
            plannedBoundaryAlarm = plannedAlarm,
            expiryBoundaryAlarm = expiryAlarm,
        )
        scheduler.scheduleAlarms(plan, emptyList())
        scheduler.scheduleAlarms(plan, listOf(plannedAlarm, expiryAlarm))
        scheduler.cancelAlarms(listOf(plannedAlarm, expiryAlarm))
    }

    @Test
    fun notificationContentPendingIntentUsesImmutableFlagsOnApi31Plus() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val presenter = AndroidPracticeNotificationPresenter(context)
        presenter.showNotification(
            NotificationShowPlan(
                occurrenceId = 99L,
                plannedAtEpochMillis = System.currentTimeMillis(),
                questionTextSnapshot = "Regression notification body",
                soundEnabled = false,
            ),
        )
        presenter.cancelPracticeNotification(99L)
    }

    @Test
    fun permissionChangedSyncSchedulesAlarmsWithoutMainThreadAccess() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        try {
            val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            DatabaseSeeder().seedFromJson(assetJson, database, TEST_ZONE_ID)
            val timeProvider = FakeTimeProvider(
                ZonedDateTime.of(2026, 8, 6, 8, 0, 0, 0, ZoneId.of(TEST_ZONE_ID)).toInstant().toEpochMilli(),
                TEST_ZONE_ID,
            )
            val runtime = TestPraktikaRuntimeBuilder.build(
                context = context,
                database = database,
                timeProvider = timeProvider,
                databaseName = "permission-sync-integration-test",
                mode = RuntimeMode.PRODUCTION,
                foregroundDriver = NoOpRuntimeForegroundDriver(),
                soundPreferenceRepository = DataStoreSoundPreferenceRepository(context),
                alarmScheduler = AndroidAlarmScheduler(context),
                permissionRepository = GrantedNotificationPermissionPolicy(),
            )
            withContext(Dispatchers.IO) {
                check(runtime.initializer.ensureInitialized())
                runtime.cycleRepository.startPractice()
            }

            withContext(Dispatchers.IO) {
                runtime.notificationCoordinator.sync(NotificationSyncReason.PERMISSION_CHANGED)
            }

            val occurrenceId = database.questionOccurrenceDao().getIncompleteOrdered().single().id
            val alarmDump = readShellOutput("dumpsys alarm")
            assertTrue(
                "Expected planned/expiry alarms for occurrence $occurrenceId",
                alarmDump.contains(occurrenceId.toString()),
            )

            val occurrence = database.questionOccurrenceDao().getById(occurrenceId)!!
            AndroidAlarmScheduler(context).cancelAlarms(
                listOf(
                    BoundaryAlarmPlan(
                        occurrenceId = occurrence.id,
                        eventType = BoundaryEventType.PLANNED_BOUNDARY,
                        triggerAtEpochMillis = occurrence.plannedAtEpochMillis,
                        plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
                    ),
                    BoundaryAlarmPlan(
                        occurrenceId = occurrence.id,
                        eventType = BoundaryEventType.EXPIRY_BOUNDARY,
                        triggerAtEpochMillis = occurrence.availableUntilEpochMillis,
                        plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
                    ),
                ),
            )
        } finally {
            database.close()
        }
    }
    // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik END

    private fun readShellOutput(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return pfd.use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    }

    private companion object {
        const val TEST_ZONE_ID = "Europe/Kiev"
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
