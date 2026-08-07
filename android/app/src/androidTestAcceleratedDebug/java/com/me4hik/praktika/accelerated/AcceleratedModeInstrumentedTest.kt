// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - instrumented accelerated tests
package com.me4hik.praktika.accelerated

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.runtime.RuntimeMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcceleratedModeInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
        context.getDatabasePath("praktika.db").delete()
    }

    @After
    fun tearDown() {
        PraktikaRuntimeHolder.resetForTests()
    }

    @Test
    fun acceleratedDatabaseNameIsIsolated() {
        val runtime = PraktikaRuntimeHolder.get(context)
        assertEquals(RuntimeMode.ACCELERATED, runtime.mode)
        assertEquals(RuntimeFactory.ACCELERATED_DATABASE_NAME, runtime.databaseName)
        assertFalse(context.getDatabasePath("praktika.db").exists())
    }

    @Test
    fun atomicFileRoundTripUtf8() = runBlocking {
        val storage = AcceleratedTimeStorage(context)
        val monotonic = FakeMonotonicTimeSource(0L, 1_000L, 1, ZONE)
        val initial = storage.loadOrCreateInitial(monotonic)
        storage.save(initial.copy(speedMultiplier = 60))
        val loaded = storage.readExisting()!!
        assertEquals(60, loaded.speedMultiplier)
    }

    @Test
    fun corruptedJsonThrowsParseException() {
        val file = File(context.filesDir, AcceleratedTimeStorage.STATE_FILE_NAME)
        file.writeText("{not-json")
        val storage = AcceleratedTimeStorage(context)
        try {
            runBlocking { storage.readExisting() }
            org.junit.Assert.fail("Expected AcceleratedClockParseException")
        } catch (_: AcceleratedClockParseException) {
        }
    }

    @Test
    fun processRecreationContinuesClockWithinSameBoot() = runBlocking {
        val anchor = 1_000_000L
        val monotonic = FakeMonotonicTimeSource(1_000L, anchor, 5, ZONE)
        val storage = AcceleratedTimeStorage(context)
        storage.save(
            AcceleratedClockState(
                bootCount = 5,
                realAnchorElapsedRealtimeMillis = 1_000L,
                virtualAnchorEpochMillis = anchor,
                speedMultiplier = 240,
                isVirtualClockPaused = false,
                pausedVirtualEpochMillis = null,
                zoneId = ZONE,
                lastCheckpointVirtualEpochMillis = anchor,
            ),
        )

        monotonic.setElapsedRealtimeMillis(2_000L)
        val provider = AcceleratedTimeProvider(storage.readExisting()!!, storage, monotonic)
        val first = provider.currentVirtualNow()
        monotonic.setElapsedRealtimeMillis(3_000L)
        val second = provider.currentVirtualNow()
        assertTrue(second > first)
    }

    @Test
    fun changedBootCountPausesClock() = runBlocking {
        val anchor = 2_000_000L
        val monotonic = FakeMonotonicTimeSource(1_000L, anchor, 1, ZONE)
        val storage = AcceleratedTimeStorage(context)
        storage.save(
            AcceleratedClockState(
                bootCount = 1,
                realAnchorElapsedRealtimeMillis = 1_000L,
                virtualAnchorEpochMillis = anchor,
                speedMultiplier = 240,
                isVirtualClockPaused = false,
                pausedVirtualEpochMillis = null,
                zoneId = ZONE,
                lastCheckpointVirtualEpochMillis = anchor + 3_600_000L,
            ),
        )
        monotonic.setBootCount(2)
        val adjusted = AcceleratedTimeProvider.applyBootPolicy(storage.readExisting()!!, monotonic, 0L)
        assertTrue(adjusted.isVirtualClockPaused)
    }

    @Test
    fun rebootVirtualNotLessThanCheckpointAndDatabaseFloor() = runBlocking {
        val checkpoint = 3_000_000L
        val monotonic = FakeMonotonicTimeSource(0L, checkpoint, 2, ZONE)
        val storage = AcceleratedTimeStorage(context)
        storage.save(
            AcceleratedClockState.createInitial(ZONE, 2_000_000L, 1, 0L).copy(
                lastCheckpointVirtualEpochMillis = checkpoint,
            ),
        )
        val adjusted = AcceleratedTimeProvider.applyBootPolicy(
            storage.readExisting()!!,
            monotonic,
            databaseFloorEpochMillis = checkpoint - 1_800_000L,
        )
        assertEquals(checkpoint, adjusted.pausedVirtualEpochMillis)
    }

    @Test
    fun speedChangeDuringAvailableDoesNotJumpVirtualOrTimestamps() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        advanceClockToSlot(clock, 11, 0)
        runtime.cycleRepository.startPractice()
        clock.pauseVirtualClock()
        val before = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val virtualBefore = clock.currentVirtualNow()
        clock.setSpeedMultiplier(600)
        val after = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(virtualBefore, clock.currentVirtualNow())
        assertEquals(before.plannedAtEpochMillis, after.plannedAtEpochMillis)
        assertEquals(before.availableUntilEpochMillis, after.availableUntilEpochMillis)
    }

    @Test
    fun pauseVirtualClockDoesNotChangePracticePause() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        advanceClockToSlot(clock, 11, 0)
        runtime.cycleRepository.startPractice()
        clock.pauseVirtualClock()
        assertFalse(runtime.database.practiceStateDao().get()!!.isPaused)
    }

    @Test
    fun pausePracticeDoesNotChangeVirtualClockPause() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        advanceClockToSlot(clock, 11, 0)
        runtime.cycleRepository.startPractice()
        runtime.cycleRepository.pausePractice()
        assertFalse(clock.currentState().isVirtualClockPaused)
    }

    @Test
    fun advanceMinutesAndReconcileChangesState() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        runtime.cycleRepository.startPractice()
        advanceClockToSlot(clock, 11, 0)
        runtime.cycleRepository.reconcile()
        val occurrence = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
    }

    @Test
    fun tickOnceReconciles() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        runtime.cycleRepository.startPractice()
        advanceClockToSlot(clock, 11, 0)
        val driver = runtime.foregroundDriver as AcceleratedCycleDriver
        driver.tickOnce()
        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
        )
    }

    @Test
    fun runtimeHolderReturnsSameRepositoryInProcess() {
        val first = PraktikaRuntimeHolder.get(context).cycleRepository
        val second = PraktikaRuntimeHolder.get(context).cycleRepository
        assertEquals(first, second)
    }

    @Test
    fun receiverRejectsUnknownCommand() = runBlocking {
        seedAcceleratedDatabase()
        PraktikaRuntimeHolder.get(context)
        try {
            AcceleratedCommandProcessor(context).process("unknown_command", Intent())
            org.junit.Assert.fail("Expected AcceleratedClockCommandException")
        } catch (_: AcceleratedClockCommandException) {
        }
    }

    @Test
    fun seedCreatesExpectedCounts() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        assertEquals(21, runtime.database.questionDao().count())
        assertEquals(3, runtime.database.scheduleSlotDao().count())
        assertEquals(1, runtime.database.practiceStateDao().get()?.let { 1 } ?: 0)
        assertEquals(0, runtime.database.questionOccurrenceDao().count())
    }

    @Test
    fun stepEventScheduledDoesNotCreateDuplicates() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        runtime.cycleRepository.startPractice()
        val processor = AcceleratedCommandProcessor(context)
        processor.process("step_event", Intent())
        assertEquals(1, runtime.database.questionOccurrenceDao().count())
        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
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

    private suspend fun seedAcceleratedDatabase() {
        val runtime = PraktikaRuntimeHolder.get(context)
        val database = runtime.database
        if (database.questionDao().count() == 0) {
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
    }

    private companion object {
        const val ZONE = "Europe/Kiev"
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
