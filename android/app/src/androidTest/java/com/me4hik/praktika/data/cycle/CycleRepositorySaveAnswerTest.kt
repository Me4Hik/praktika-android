// 05.08.2026 Answer Save cursor by Me4Hik START - instrumented tests saveAnswer
package com.me4hik.praktika.data.cycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositorySaveAnswerTest {
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
    fun saveOneWordAnswer() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(occurrence.id, "Да")

        val answer = database.answerDao().getByOccurrenceId(occurrence.id)!!
        assertEquals("Да", answer.text)
    }

    @Test
    fun saveMultilineUnicodeAnswer() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val text = "Да\n\nСтопы 👣 \"земля\""
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(occurrence.id, text)

        assertEquals(text, database.answerDao().getByOccurrenceId(occurrence.id)!!.text)
    }

    @Test
    fun whitespaceOnlyAnswerIsRejected() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        try {
            repository.saveAnswer(occurrence.id, "   ")
            fail("Expected CycleAnswerNotAllowedException")
        } catch (exception: CycleAnswerNotAllowedException) {
            assertEquals(CycleAnswerNotAllowedReason.BLANK, exception.reason)
        }
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun answerTextStoredVerbatimWithSpaces() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val text = "  warm  "
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(occurrence.id, text)

        assertEquals(text, database.answerDao().getByOccurrenceId(occurrence.id)!!.text)
    }

    @Test
    fun saveWithMatchingIdSetsAnsweredAndNextOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val saveAt = epochAt(11, 30, 0)
        timeProvider.setEpochMillis(saveAt)
        repository.saveAnswer(occurrence.id, "Answer")

        val first = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, first.status)
        assertEquals(saveAt, first.completedAtEpochMillis)
        assertEquals(saveAt, database.answerDao().getByOccurrenceId(occurrence.id)!!.createdAtEpochMillis)
        assertEquals(2, database.questionOccurrenceDao().count())
        assertNull(first.openedAtEpochMillis)
    }

    @Test
    fun saveWithWrongIdDoesNotMutateDatabase() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(occurrence.id, "First")

        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        try {
            repository.saveAnswer(occurrence.id, "Stale")
            fail("Expected CycleAnswerNotAllowedException")
        } catch (exception: CycleAnswerNotAllowedException) {
            assertEquals(CycleAnswerNotAllowedReason.EXPECTED_ID_MISMATCH, exception.reason)
        }

        assertEquals(1, database.answerDao().count())
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, second.status)
    }

    @Test
    fun staleRouteDoesNotAnswerNewOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val staleId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.skipAvailableByUser(staleId)

        val current = database.questionOccurrenceDao().getIncompleteOrdered().single()
        try {
            repository.saveAnswer(staleId, "Wrong target")
            fail("Expected CycleAnswerNotAllowedException")
        } catch (exception: CycleAnswerNotAllowedException) {
            assertEquals(CycleAnswerNotAllowedReason.EXPECTED_ID_MISMATCH, exception.reason)
        }

        assertEquals(QuestionOccurrenceStatus.SCHEDULED, current.status)
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun saveAdvancesCursorOnce() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(occurrence.id, "Answer")

        val state = database.practiceStateDao().get()!!
        assertEquals(1, state.currentCycleNumber)
        assertEquals(3, state.nextCyclePosition)
    }

    @Test
    fun skipAfterSaveIsRejected() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(occurrence.id, "Answer")

        try {
            repository.skipAvailableByUser(occurrence.id)
            fail("Expected CycleSkipNotAllowedException")
        } catch (_: CycleSkipNotAllowedException) {
        }
    }

    @Test
    fun existingAnswerIsRejected() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrence.id,
                text = "Existing",
                createdAtEpochMillis = epochAt(11, 15, 0),
            ),
        )
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        try {
            repository.saveAnswer(occurrence.id, "New")
            fail("Expected CycleAnswerNotAllowedException")
        } catch (exception: CycleAnswerNotAllowedException) {
            assertEquals(CycleAnswerNotAllowedReason.ANSWER_ALREADY_EXISTS, exception.reason)
        }
    }

    @Test
    fun concurrentSaveSameIdAllowsOnlyOneSuccess() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrenceId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        timeProvider.setEpochMillis(epochAt(11, 30, 0))

        val results = listOf(
            async { runCatching { repository.saveAnswer(occurrenceId, "Race") } },
            async { runCatching { repository.saveAnswer(occurrenceId, "Race") } },
        ).awaitAll()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals(1, database.answerDao().count())
        assertEquals(1, database.questionOccurrenceDao().countByStatus(QuestionOccurrenceStatus.ANSWERED))
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)
    }

    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - repeat questionId new occurrence save
    @Test
    fun repeatQuestionIdAllowsSaveOnNewOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        val cycleOneOccurrenceId = database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Question 1 cycle 1",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(11, 0, 0),
                availableUntilEpochMillis = epochAt(15, 0, 0),
                openedAtEpochMillis = null,
                completedAtEpochMillis = epochAt(11, 30, 0),
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = ZONE_KIEV,
            ),
        )
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = cycleOneOccurrenceId,
                text = "Answer A",
                createdAtEpochMillis = epochAt(11, 30, 0),
            ),
        )
        val cycleTwoOccurrenceId = database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Question 1 cycle 2",
                cycleNumber = 2,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(11, 0, 0),
                availableUntilEpochMillis = epochAt(15, 0, 0),
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.AVAILABLE,
                zoneId = ZONE_KIEV,
            ),
        )
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = epochAt(11, 0, 0),
                currentCycleNumber = 2,
                nextCyclePosition = 2,
                lastProcessedAtEpochMillis = epochAt(11, 0, 0),
                activeZoneId = ZONE_KIEV,
            ),
        )

        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.saveAnswer(cycleTwoOccurrenceId, "Answer B")

        val answerA = database.answerDao().getByOccurrenceId(cycleOneOccurrenceId)!!
        val answerB = database.answerDao().getByOccurrenceId(cycleTwoOccurrenceId)!!
        assertEquals("Answer A", answerA.text)
        assertEquals("Answer B", answerB.text)
        assertEquals(2, database.answerDao().count())
        assertEquals(
            QuestionOccurrenceStatus.ANSWERED,
            database.questionOccurrenceDao().getById(cycleTwoOccurrenceId)!!.status,
        )
        val cycleTwoQuestionId = database.questionOccurrenceDao().getById(cycleTwoOccurrenceId)!!.questionId
        val cycleOneQuestionId = database.questionOccurrenceDao().getById(cycleOneOccurrenceId)!!.questionId
        assertEquals(1, cycleOneQuestionId)
        assertEquals(cycleOneQuestionId, cycleTwoQuestionId)
        assertEquals(3, database.questionOccurrenceDao().count())
    }
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

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

    private fun epochAt(hour: Int, minute: Int, second: Int, nano: Int = 0): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, nano, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
