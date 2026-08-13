// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - repository integration tests
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositoryStage11Test {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
        repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun updateBeforeStartThenStartUsesCustomSchedule() = runBlocking {
        repository.updateSchedule(defaultUpdates(630, 860, 1210))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertEquals(epochAt(10, 30, 0), occurrence.plannedAtEpochMillis)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun customSameMinuteSlotStartsAvailable() = runBlocking {
        timeProvider.setEpochMillis(epochAt(10, 30, 0))
        repository.updateSchedule(defaultUpdates(630, 860, 1210))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
    }

    @Test
    fun customScheduleAfterLastSlotStartsNextDay() = runBlocking {
        timeProvider.setEpochMillis(epochAt(20, 30, 0))
        repository.updateSchedule(defaultUpdates(630, 860, 1210))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertEquals(epochAt(10, 30, 0, day = 5), occurrence.plannedAtEpochMillis)
    }

    @Test
    fun concurrentDoubleStartCreatesSingleOccurrence() = runBlocking {
        val results = listOf(
            async { runCatching { repository.startPractice() } },
            async { runCatching { repository.startPractice() } },
        ).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals(1, database.questionOccurrenceDao().count())
        assertTrue(database.practiceStateDao().get()!!.isPracticeStarted)
    }

    @Test
    fun alreadyStartedDoesNotMutateDatabase() = runBlocking {
        repository.startPractice()
        val before = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        try {
            repository.startPractice()
        } catch (_: CycleAlreadyStartedException) {
        }
        val after = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(before.id, after.id)
        assertEquals(before.plannedAtEpochMillis, after.plannedAtEpochMillis)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun startAdvancesCursorOnce() = runBlocking {
        repository.startPractice()
        val state = database.practiceStateDao().get()!!
        assertEquals(1, state.currentCycleNumber)
        assertEquals(2, state.nextCyclePosition)
    }

    @Test
    fun roomSchemaVersionRemainsTwo() {
        assertEquals(2, database.openHelper.readableDatabase.version)
    }

    @Test
    fun quickCheckOkAfterCustomStart() = runBlocking {
        repository.updateSchedule(defaultUpdates(630, 860, 1210))
        repository.startPractice()
        database.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
    }

    private suspend fun seedBaseData() {
        val questions = (1..21).map { position ->
            QuestionEntity(
                id = position,
                cyclePosition = position,
                text = "Question $position",
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

    private fun defaultUpdates(first: Int, second: Int, third: Int): List<ScheduleSlotUpdate> {
        return listOf(
            ScheduleSlotUpdate(1, first),
            ScheduleSlotUpdate(2, second),
            ScheduleSlotUpdate(3, third),
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int, day: Int = 4): Long {
        return ZonedDateTime.of(2026, 8, day, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
