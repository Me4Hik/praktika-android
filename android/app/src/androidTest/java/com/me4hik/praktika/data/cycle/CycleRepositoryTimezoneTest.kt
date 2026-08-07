// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - timezone reconcile tests
package com.me4hik.praktika.data.cycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositoryTimezoneTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
        repository = CycleRepository(database, timeProvider)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun timezoneChangeScheduledReAnchorsSameOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        val before = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, before.status)
        val beforeId = before.id
        val beforePlanned = before.plannedAtEpochMillis

        timeProvider.setZoneId(ZONE_LONDON)
        repository.syncEnvironmentAndReconcile()

        val after = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(beforeId, after.id)
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)
        assertEquals(ZONE_LONDON, database.practiceStateDao().get()!!.activeZoneId)
        assertNotEquals(beforePlanned, after.plannedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, after.status)
    }

    @Test
    fun timezoneChangeAvailablePreservesHistoricalFields() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val before = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, before.status)
        val beforeAvailableUntil = before.availableUntilEpochMillis

        timeProvider.setZoneId(ZONE_LONDON)
        repository.syncEnvironmentAndReconcile()

        val after = database.questionOccurrenceDao().getById(before.id)!!
        assertEquals(before.id, after.id)
        assertEquals(before.plannedAtEpochMillis, after.plannedAtEpochMillis)
        assertEquals(before.scheduleSlotIndex, after.scheduleSlotIndex)
        assertEquals(before.cyclePosition, after.cyclePosition)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.status)
        assertEquals(ZONE_LONDON, database.practiceStateDao().get()!!.activeZoneId)
        assertNotEquals(beforeAvailableUntil, after.availableUntilEpochMillis)
    }

    @Test
    fun timezoneChangePausedUpdatesZoneOnly() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        repository.pausePractice()
        val before = database.questionOccurrenceDao().getIncompleteOrdered().single()

        timeProvider.setZoneId(ZONE_LONDON)
        repository.syncEnvironmentAndReconcile()

        val after = database.questionOccurrenceDao().getById(before.id)!!
        assertEquals(before.status, after.status)
        assertEquals(before.plannedAtEpochMillis, after.plannedAtEpochMillis)
        assertEquals(before.availableUntilEpochMillis, after.availableUntilEpochMillis)
        assertNull(after.openedAtEpochMillis)
        assertEquals(ZONE_LONDON, database.practiceStateDao().get()!!.activeZoneId)
    }

    private suspend fun seedBaseData() {
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
                activeZoneId = ZONE_KIEV,
                seedVersion = 1,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int = 0): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
        const val ZONE_LONDON = "Europe/London"
    }
}
// 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
