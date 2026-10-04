// 05.08.2026 Question And Skip cursor by Me4Hik START - instrumented tests QuestionReadRepository
package com.me4hik.praktika.data.read

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionReadRepositoryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomQuestionReadRepository
    private lateinit var cycleRepository: CycleRepository
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomQuestionReadRepository(database)
        runBlocking { seedBase() }
        timeProvider = FakeTimeProvider(epochAt(8, 0), ZONE)
        cycleRepository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun initialReadByIdIsCurrentForAvailable() = runBlocking {
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        markStarted()
        val result = repository.observeOccurrence(occurrenceId).first()
        val found = result as QuestionOccurrenceReadResult.Found
        assertTrue(found.snapshot.isCurrent)
        assertEquals("Snapshot text", found.snapshot.questionTextSnapshot)
    }

    @Test
    fun completedOccurrenceIsNotCurrent() = runBlocking {
        val occurrenceId = insertOccurrence(
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            completedAt = epochAt(12, 0),
        )
        markStarted()
        insertOccurrence(
            status = QuestionOccurrenceStatus.SCHEDULED,
            cyclePosition = 2,
            plannedAt = epochAt(15, 0),
            availableUntil = epochAt(19, 0),
        )
        val result = repository.observeOccurrence(occurrenceId).first() as QuestionOccurrenceReadResult.Found
        assertFalse(result.snapshot.isCurrent)
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, result.snapshot.status)
    }

    @Test
    fun missingOccurrenceIsNotCorruption() = runBlocking {
        markStarted()
        insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        val result = repository.observeOccurrence(999L).first()
        assertEquals(QuestionOccurrenceReadResult.Missing, result)
    }

    @Test
    fun scheduledSnapshotKeepsTextForRepositoryLayer() = runBlocking {
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)
        markStarted()
        val result = repository.observeOccurrence(occurrenceId).first() as QuestionOccurrenceReadResult.Found
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, result.snapshot.status)
        assertEquals("Snapshot text", result.snapshot.questionTextSnapshot)
    }

    @Test
    fun skipChangesIsCurrentForOldOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0))
        cycleRepository.startPractice()
        val firstId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        timeProvider.setEpochMillis(epochAt(11, 30))
        cycleRepository.skipAvailableByUser(firstId)

        val stale = repository.observeOccurrence(firstId).first() as QuestionOccurrenceReadResult.Found
        assertFalse(stale.snapshot.isCurrent)
        val current = database.questionOccurrenceDao().getIncompleteOrdered().single()
        val currentRead = repository.observeOccurrence(current.id).first() as QuestionOccurrenceReadResult.Found
        assertTrue(currentRead.snapshot.isCurrent)
    }

    @Test
    fun multipleIncompleteThrowsCorruption() = runBlocking {
        markStarted()
        insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED, cyclePosition = 1)
        insertOccurrence(
            status = QuestionOccurrenceStatus.AVAILABLE,
            cyclePosition = 2,
            plannedAt = epochAt(15, 0),
            availableUntil = epochAt(19, 0),
        )
        try {
            repository.observeOccurrence(1L).first()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
            // expected
        }
    }

    private suspend fun seedBase() {
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

    private suspend fun markStarted() {
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                practiceStartedAtEpochMillis = epochAt(8, 0),
                currentCycleNumber = 1,
                nextCyclePosition = 2,
            ),
        )
    }

    private suspend fun insertOccurrence(
        status: QuestionOccurrenceStatus,
        cyclePosition: Int = 1,
        plannedAt: Long = epochAt(11, 0),
        availableUntil: Long = epochAt(15, 0),
        completedAt: Long? = null,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Snapshot text",
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                completedAtEpochMillis = completedAt,
                status = status,
                zoneId = ZONE,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int): Long {
        return ZonedDateTime.of(2026, 8, 5, hour, minute, 0, 0, ZoneId.of(ZONE))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE = "Europe/Kiev"
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
