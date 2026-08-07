// 05.08.2026 Answer Save cursor by Me4Hik START - instrumented tests AnswerReadRepository
package com.me4hik.praktika.data.read

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnswerReadRepositoryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomAnswerReadRepository
    private lateinit var cycleRepository: CycleRepository
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomAnswerReadRepository(database)
        runBlocking { seedBase() }
        timeProvider = FakeTimeProvider(epochAt(8, 0), ZONE)
        cycleRepository = CycleRepository(database, timeProvider)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun initialAvailableCurrentSnapshot() = runBlocking {
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        markStarted()
        val result = repository.observeSnapshot(occurrenceId).first() as AnswerReadResult.Found
        assertTrue(result.snapshot.isCurrent)
        assertFalse(result.snapshot.isPaused)
        assertFalse(result.snapshot.hasExistingAnswer)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, result.snapshot.occurrence!!.status)
    }

    @Test
    fun missingOccurrence() = runBlocking {
        markStarted()
        insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        val result = repository.observeSnapshot(999L).first()
        assertEquals(AnswerReadResult.Missing, result)
    }

    @Test
    fun scheduledOccurrenceReadable() = runBlocking {
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)
        markStarted()
        val result = repository.observeSnapshot(occurrenceId).first() as AnswerReadResult.Found
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, result.snapshot.occurrence!!.status)
    }

    @Test
    fun pausedPracticeFlag() = runBlocking {
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                isPaused = true,
                currentCycleNumber = 1,
                nextCyclePosition = 2,
            ),
        )
        val result = repository.observeSnapshot(occurrenceId).first() as AnswerReadResult.Found
        assertTrue(result.snapshot.isPaused)
    }

    @Test
    fun completedOccurrenceIsNotCurrent() = runBlocking {
        val occurrenceId = insertOccurrence(
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = epochAt(12, 0),
        )
        markStarted()
        insertOccurrence(
            status = QuestionOccurrenceStatus.SCHEDULED,
            cyclePosition = 2,
            plannedAt = epochAt(15, 0),
            availableUntil = epochAt(19, 0),
        )
        val result = repository.observeSnapshot(occurrenceId).first() as AnswerReadResult.Found
        assertFalse(result.snapshot.isCurrent)
    }

    @Test
    fun answeredWithoutAnswerIsAllowed() = runBlocking {
        val occurrenceId = insertOccurrence(
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = epochAt(12, 0),
        )
        markStarted()
        insertOccurrence(
            status = QuestionOccurrenceStatus.SCHEDULED,
            cyclePosition = 2,
            plannedAt = epochAt(15, 0),
            availableUntil = epochAt(19, 0),
        )
        val result = repository.observeSnapshot(occurrenceId).first() as AnswerReadResult.Found
        assertFalse(result.snapshot.hasExistingAnswer)
    }

    @Test
    fun answerAfterSaveVisibleInSnapshot() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0))
        cycleRepository.startPractice()
        val occurrenceId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        timeProvider.setEpochMillis(epochAt(11, 30))
        cycleRepository.saveAnswer(occurrenceId, "Saved")

        val result = repository.observeSnapshot(occurrenceId).first() as AnswerReadResult.Found
        assertTrue(result.snapshot.hasExistingAnswer)
        assertEquals(QuestionOccurrenceStatus.ANSWERED, result.snapshot.occurrence!!.status)
    }

    @Test
    fun answerWithAvailableIsCorruption() = runBlocking {
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        markStarted()
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Orphan",
                createdAtEpochMillis = epochAt(11, 0),
            ),
        )
        try {
            repository.observeSnapshot(occurrenceId).first()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
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
                ScheduleSlotEntity(1, 660),
                ScheduleSlotEntity(2, 900),
                ScheduleSlotEntity(3, 1140),
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
                questionId = cyclePosition,
                questionTextSnapshot = "Snapshot text",
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = completedAt,
                status = status,
                zoneId = ZONE,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, 0, 0, ZoneId.of(ZONE))
            .toInstant()
            .toEpochMilli()
    }

    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - prior answers read API
    @Test
    fun hasPriorAnswersFalseForFirstQuestion() = runBlocking {
        markStarted()
        assertFalse(repository.hasPriorAnswersForQuestion(1))
    }

    @Test
    fun hasPriorAnswersTrueWhenEarlierAnswerExists() = runBlocking {
        markStarted()
        val occurrenceId = insertOccurrence(status = QuestionOccurrenceStatus.ANSWERED)
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Earlier answer",
                createdAtEpochMillis = epochAt(11, 30),
            ),
        )
        assertTrue(repository.hasPriorAnswersForQuestion(1))
    }
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

    private companion object {
        const val ZONE = "Europe/Kiev"
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
