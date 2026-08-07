// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - instrumented tests question repository
package com.me4hik.praktika.data.read

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveReadRepositoryQuestionTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomArchiveReadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomArchiveReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun selectedQuestionIdReturnsOnlyItsAnswers() = runBlocking {
        seedPracticeStarted()
        val occurrenceOne = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 1,
            snapshot = "Q1",
            plannedAt = 1000L,
        )
        val occurrenceTwo = insertOccurrence(
            questionId = 2,
            cyclePosition = 2,
            cycleNumber = 1,
            snapshot = "Q2",
            plannedAt = 2000L,
        )
        insertAnswer(occurrenceOne, "Answer 1", 1100L)
        insertAnswer(occurrenceTwo, "Answer 2", 2100L)

        val entries = repository.observeEntriesForQuestion(1).first()
        assertEquals(1, entries.size)
        assertEquals(1, entries.single().questionId)
        assertEquals("Answer 1", entries.single().answerText)
    }

    @Test
    fun occurrenceWithoutAnswerIsExcluded() = runBlocking {
        seedPracticeStarted()
        val answeredOccurrence = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 1,
            snapshot = "Answered",
            plannedAt = 1000L,
        )
        insertOccurrence(
            questionId = 1,
            cyclePosition = 2,
            cycleNumber = 2,
            snapshot = "Skipped",
            plannedAt = 2000L,
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
        )
        insertAnswer(answeredOccurrence, "Only answer", 1100L)

        val entries = repository.observeEntriesForQuestion(1).first()
        assertEquals(1, entries.size)
        assertEquals("Only answer", entries.single().answerText)
    }

    @Test
    fun multipleCyclesReturnAllAnswersInAscOrder() = runBlocking {
        seedPracticeStarted()
        val cycleOne = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 1,
            snapshot = "Snapshot cycle 1",
            plannedAt = 1000L,
        )
        val cycleTwo = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 2,
            snapshot = "Snapshot cycle 2",
            plannedAt = 5000L,
        )
        val answerOne = insertAnswer(cycleOne, "Cycle 1 answer", 1100L)
        val answerTwo = insertAnswer(cycleTwo, "Cycle 2 answer", 5100L)

        val entries = repository.observeEntriesForQuestion(1).first()
        assertEquals(listOf(answerOne, answerTwo), entries.map { it.answerId })
        assertEquals(listOf(1, 2), entries.map { it.cycleNumber })
        assertEquals(listOf("Snapshot cycle 1", "Snapshot cycle 2"), entries.map { it.questionText })
    }

    @Test
    fun historicalSnapshotsPreservedAfterQuestionUpdate() = runBlocking {
        database.questionDao().insertAll(
            listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Live text")),
        )
        database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 1,
            snapshot = "Frozen snapshot",
            plannedAt = 1000L,
        )
        insertAnswer(occurrenceId, "Answer", 1100L)
        database.questionDao().updateText(id = 1, text = "Updated live text")

        val entry = repository.observeEntriesForQuestion(1).first().single()
        assertEquals("Frozen snapshot", entry.questionText)
        assertNotEquals("Updated live text", entry.questionText)
    }

    @Test
    fun sameTimestampUsesAnswerIdAscTieBreaker() = runBlocking {
        seedPracticeStarted()
        val firstOccurrence = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 1,
            snapshot = "First",
            plannedAt = 1000L,
        )
        val secondOccurrence = insertOccurrence(
            questionId = 1,
            cyclePosition = 2,
            cycleNumber = 1,
            snapshot = "Second",
            plannedAt = 2000L,
        )
        val lowerAnswerId = insertAnswer(firstOccurrence, "Lower id", 1500L)
        val higherAnswerId = insertAnswer(secondOccurrence, "Higher id", 1500L)
        assertTrue(higherAnswerId > lowerAnswerId)

        val orderedIds = repository.observeEntriesForQuestion(1).first().map { it.answerId }
        assertEquals(listOf(lowerAnswerId, higherAnswerId), orderedIds)
    }

    @Test
    fun deleteAnswerRemovesRowReactively() = runBlocking {
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            cycleNumber = 1,
            snapshot = "Q1",
            plannedAt = 1000L,
        )
        val answerId = insertAnswer(occurrenceId, "Delete me", 1100L)
        val flow = repository.observeEntriesForQuestion(1)
        assertEquals(1, flow.first().size)
        database.answerDao().deleteById(answerId)
        assertTrue(flow.first { it.isEmpty() }.isEmpty())
    }

    private suspend fun seedPracticeStarted() {
        if (database.questionDao().getById(1) == null) {
            database.questionDao().insertAll(
                listOf(
                    QuestionEntity(id = 1, cyclePosition = 1, text = "Question 1"),
                    QuestionEntity(id = 2, cyclePosition = 2, text = "Question 2"),
                ),
            )
        }
        if (database.scheduleSlotDao().getAllOrderedByTime().isEmpty()) {
            database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        }
        if (database.practiceStateDao().get() == null) {
            database.practiceStateDao().insert(
                PracticeStateEntity(
                    id = 1,
                    isPracticeStarted = true,
                    isPaused = false,
                    practiceStartedAtEpochMillis = 100L,
                    currentCycleNumber = 1,
                    nextCyclePosition = 2,
                    lastProcessedAtEpochMillis = 100L,
                    pausedAtEpochMillis = null,
                    activeZoneId = "UTC",
                    seedVersion = 1,
                ),
            )
        }
    }

    private suspend fun insertOccurrence(
        questionId: Int,
        cyclePosition: Int,
        cycleNumber: Int,
        snapshot: String,
        plannedAt: Long,
        status: QuestionOccurrenceStatus = QuestionOccurrenceStatus.ANSWERED,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = snapshot,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = plannedAt + 1000L,
                status = status,
                zoneId = "UTC",
            ),
        )
    }

    private suspend fun insertAnswer(
        occurrenceId: Long,
        text: String,
        createdAt: Long,
    ): Long {
        return database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = text,
                createdAtEpochMillis = createdAt,
            ),
        )
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
