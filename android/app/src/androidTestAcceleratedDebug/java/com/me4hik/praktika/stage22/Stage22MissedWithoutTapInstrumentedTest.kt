// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - missed notification without tap
package com.me4hik.praktika.stage22

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.NotificationTapDecision
import com.me4hik.praktika.notification.NotificationTapPolicy
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage22MissedWithoutTapInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    @After
    fun tearDown() {
        PraktikaRuntimeHolder.resetForTests()
    }

    @Test
    fun availableOccurrenceBecomesMissedWithoutTapAfterBoundaryReconcile() = runBlocking {
        seedAcceleratedDatabase()
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        runtime.cycleRepository.startPractice()

        advanceClockToSlot(clock, 11, 0)
        runtime.cycleRepository.reconcile()

        val available = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, available.status)
        val availableId = available.id
        val availablePlannedAt = available.plannedAtEpochMillis
        val availableUntil = available.availableUntilEpochMillis

        clock.advanceToVirtualEpochMillis(availableUntil + 1L)
        runtime.cycleRepository.reconcile()

        val missed = runtime.database.questionOccurrenceDao().getById(availableId)!!
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, missed.status)
        assertNull(runtime.database.answerDao().getByOccurrenceId(availableId))

        val next = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(2, next.cyclePosition)
        assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)
        assertTrue(next.id != availableId)

        val practiceState = runtime.database.practiceStateDao().get()!!
        val staleOccurrence = runtime.database.questionOccurrenceDao().getById(availableId)!!
        val currentIncomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        val tapDecision = NotificationTapPolicy.evaluate(
            practiceState = practiceState,
            currentIncomplete = currentIncomplete,
            targetOccurrence = staleOccurrence,
            expectedOccurrenceId = availableId,
            expectedPlannedAtEpochMillis = availablePlannedAt,
        )
        assertTrue(tapDecision is NotificationTapDecision.Ignore)

        val nextAfterStale = runtime.database.questionOccurrenceDao().getById(next.id)!!
        assertEquals(next.status, nextAfterStale.status)
        assertEquals(next.openedAtEpochMillis, nextAfterStale.openedAtEpochMillis)
    }

    private suspend fun seedAcceleratedDatabase() {
        val runtime = PraktikaRuntimeHolder.get(context)
        seedInMemory(runtime.database)
    }

    private suspend fun seedInMemory(database: PraktikaDatabase) {
        val questions = (1..21).map { index ->
            QuestionEntity(
                id = index,
                cyclePosition = index,
                text = "Question $index",
                isActive = true,
            )
        }
        database.questionDao().insertAll(questions)
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

    private companion object {
        const val ZONE = "Europe/Kiev"
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END
