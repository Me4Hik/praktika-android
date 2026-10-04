// 05.08.2026 Accelerated Driver Fix cursor by Me4Hik START - instrumented driver tests
package com.me4hik.praktika.accelerated

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcceleratedCycleDriverInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    @After
    fun tearDown() {
        PraktikaRuntimeHolder.resetForTests()
    }

    @Test
    fun tickOnceFromMainThreadDoesNotThrow() = runBlocking(Dispatchers.Main) {
        seedAcceleratedDatabase()
        val driver = driver()
        driver.tickOnce()
    }

    @Test
    fun tickOnceTransitionsScheduledToAvailable() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        runtime.cycleRepository.startPractice()
        driver().tickOnce()
        val occurrence = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)

        advanceClockToSlot(clock, 11, 0)
        driver().tickOnce()

        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
        )
    }

    @Test
    fun tickOnceTransitionsAvailableToMissed() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        runtime.cycleRepository.startPractice()
        advanceClockToSlot(clock, 11, 0)
        driver().tickOnce()
        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
        )

        val availableUntil = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            .availableUntilEpochMillis
        clock.advanceToVirtualEpochMillis(availableUntil)
        driver().tickOnce()

        assertEquals(
            QuestionOccurrenceStatus.MISSED_BY_TIME,
            runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
        )
        assertEquals(2, runtime.database.questionOccurrenceDao().count())
    }

    @Test
    fun tickOnceWhilePracticeNotStartedIsNormal() = runBlocking(Dispatchers.Main) {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        driver().tickOnce()
        assertEquals(0, runtime.database.questionOccurrenceDao().count())
    }

    @Test
    fun pausedVirtualTimeDoesNotIncreaseOccurrencesOnTick() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        runtime.cycleRepository.startPractice()
        val afterStart = runtime.database.questionOccurrenceDao().count()
        driver().tickOnce()
        val afterFirstTick = runtime.database.questionOccurrenceDao().count()
        driver().tickOnce()
        assertEquals(afterStart, afterFirstTick)
        assertEquals(afterFirstTick, runtime.database.questionOccurrenceDao().count())
    }

    @Test
    fun repeatedForegroundStartDoesNotCreateSecondLoop() = runBlocking {
        seedAcceleratedDatabase()
        val driver = driver()
        val first: Job = launch(Dispatchers.Main) {
            driver.runWhileForeground()
        }
        val second: Job = launch(Dispatchers.Main) {
            driver.runWhileForeground()
        }
        delay(100)
        first.cancel()
        second.cancel()
        first.join()
        second.join()
    }

    @Test
    fun runWhileForegroundCancellationCompletesCleanly() = runBlocking {
        seedAcceleratedDatabase()
        val driver = driver()
        val job: Job = launch(Dispatchers.Main) {
            driver.runWhileForeground()
        }
        delay(100)
        job.cancel()
        try {
            job.join()
        } catch (_: CancellationException) {
        }
        assertTrue(job.isCancelled)
    }

    @Test
    fun inMemoryDriverTickOnceFromMainThread() = runBlocking(Dispatchers.Main) {
        val database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        seedInMemory(database)
        val clock = RecordingClockController()
        val repository = CycleRepository(database, clock, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        val driver = AcceleratedCycleDriver(repository, clock)

        driver.tickOnce()

        assertEquals(0, database.questionOccurrenceDao().count())
        database.close()
    }

    private fun driver(): AcceleratedCycleDriver {
        return PraktikaRuntimeHolder.get(context).foregroundDriver as AcceleratedCycleDriver
    }

    private suspend fun seedAcceleratedDatabase() {
        val runtime = PraktikaRuntimeHolder.get(context)
        seedInMemory(runtime.database)
    }

    private suspend fun seedInMemory(database: PraktikaDatabase) {
        if (database.questionDao().count() > 0) {
            return
        }
        database.questionDao().insertAll(
            (1..21).map { index ->
                QuestionEntity(id = index, cyclePosition = index, text = "Question $index", isActive = true)
            },
        )
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
                ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
            ),
        )
        database.practiceStateDao().insert(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = ZONE,
                seedVersion = 1,
            ),
        )
    }

    private suspend fun advanceClockToSlot(clock: AcceleratedTimeProvider, hour: Int, minute: Int = 0) {
        clock.resumeVirtualClock()
        val target = slotOnVirtualDay(clock, hour, minute)
        if (target > clock.currentVirtualNow()) {
            clock.advanceToVirtualEpochMillis(target)
        }
    }

    private fun slotOnVirtualDay(clock: AcceleratedTimeProvider, hour: Int, minute: Int): Long {
        val zone = ZoneId.of(clock.currentZoneId())
        val date = Instant.ofEpochMilli(clock.currentVirtualNow()).atZone(zone).toLocalDate()
        return ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
    }

    private class RecordingClockController : AcceleratedClockController, TimeProvider {
        override fun nowEpochMillis(): Long = currentVirtualNow()

        override fun currentZoneId(): String = ZONE

        override fun pauseVirtualClock() = Unit
        override fun resumeVirtualClock() = Unit
        override fun setSpeedMultiplier(multiplier: Int) = Unit
        override fun advanceByVirtualMinutes(minutes: Long) = Unit
        override fun advanceToVirtualEpochMillis(target: Long) = Unit
        override fun alignVirtualEpochMillis(targetEpochMillis: Long) = Unit
        override fun checkpoint() = Unit
        override fun currentVirtualNow(): Long = epochAt(8, 0)
        override fun currentState(): AcceleratedClockState = AcceleratedClockState.createInitial(
            zoneId = ZONE,
            virtualStartEpochMillis = epochAt(8, 0),
            bootCount = 1,
            realAnchorElapsedRealtimeMillis = 0L,
        )

        private fun epochAt(hour: Int, minute: Int): Long {
            return ZonedDateTime.of(2026, 8, 5, hour, minute, 0, 0, ZoneId.of(ZONE)).toInstant().toEpochMilli()
        }
    }

    private companion object {
        const val ZONE = "Europe/Kiev"
    }
}
// 05.08.2026 Accelerated Driver Fix cursor by Me4Hik END
