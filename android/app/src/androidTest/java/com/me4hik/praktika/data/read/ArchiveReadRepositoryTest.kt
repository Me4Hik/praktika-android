// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik START - instrumented tests ArchiveReadRepository
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
class ArchiveReadRepositoryTest {
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
    fun emptyArchiveWhenNoAnswers() = runBlocking {
        seedPracticeStarted()
        insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
        )
        insertOccurrence(
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.SCHEDULED,
            plannedAt = 5000L,
        )

        val entries = repository.observeEntries().first()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun oneAnswerReturnsCorrectContext() = runBlocking {
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            questionTextSnapshot = "Snapshot text",
            plannedAt = 1000L,
            completedAt = 1500L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "My answer",
                createdAtEpochMillis = 1500L,
            ),
        )

        val entry = repository.observeEntries().first().single()
        assertEquals(answerId, entry.answerId)
        assertEquals(occurrenceId, entry.occurrenceId)
        assertEquals(1, entry.questionId)
        assertEquals("Snapshot text", entry.questionText)
        assertEquals("My answer", entry.answerText)
        assertEquals(1500L, entry.answeredAtEpochMillis)
        assertEquals(1000L, entry.plannedAtEpochMillis)
        assertEquals(1, entry.cycleNumber)
        assertEquals(1, entry.cyclePosition)
    }

    @Test
    fun orderingNewestFirstWithStableTieBreaker() = runBlocking {
        seedPracticeStarted()
        val occurrenceOne = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 1000L,
        )
        val occurrenceTwo = insertOccurrence(
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 2000L,
        )
        val occurrenceThree = insertOccurrence(
            cyclePosition = 3,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 3000L,
        )

        val answerIdOldest = database.answerDao().insert(
            AnswerEntity(occurrenceId = occurrenceOne, text = "Oldest", createdAtEpochMillis = 1000L),
        )
        val answerIdNewest = database.answerDao().insert(
            AnswerEntity(occurrenceId = occurrenceThree, text = "Newest", createdAtEpochMillis = 3000L),
        )
        val answerIdMiddle = database.answerDao().insert(
            AnswerEntity(occurrenceId = occurrenceTwo, text = "Middle", createdAtEpochMillis = 2000L),
        )

        val orderedIds = repository.observeEntries().first().map { it.answerId }
        assertEquals(listOf(answerIdNewest, answerIdMiddle, answerIdOldest), orderedIds)

        val tieOccurrenceOne = insertOccurrence(
            cyclePosition = 4,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 4000L,
        )
        val tieOccurrenceTwo = insertOccurrence(
            cyclePosition = 5,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 5000L,
        )
        val lowerAnswerId = database.answerDao().insert(
            AnswerEntity(occurrenceId = tieOccurrenceOne, text = "Tie A", createdAtEpochMillis = 4000L),
        )
        val higherAnswerId = database.answerDao().insert(
            AnswerEntity(occurrenceId = tieOccurrenceTwo, text = "Tie B", createdAtEpochMillis = 4000L),
        )
        assertTrue(higherAnswerId > lowerAnswerId)

        val tieBreakIds = repository.observeEntries().first()
            .filter { it.answeredAtEpochMillis == 4000L }
            .map { it.answerId }
        assertEquals(listOf(higherAnswerId, lowerAnswerId), tieBreakIds)
    }

    @Test
    fun onlyRowsWithExistingAnswersAreReturned() = runBlocking {
        seedPracticeStarted()
        val answeredWithAnswer = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 1000L,
            completedAt = 1100L,
        )
        insertOccurrence(
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 2000L,
            completedAt = 2100L,
        )
        insertOccurrence(
            cyclePosition = 3,
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            plannedAt = 3000L,
            completedAt = 3100L,
        )
        insertOccurrence(
            cyclePosition = 4,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            plannedAt = 4000L,
            completedAt = 4100L,
        )
        insertOccurrence(
            cyclePosition = 5,
            status = QuestionOccurrenceStatus.AVAILABLE,
            plannedAt = 5000L,
        )
        insertOccurrence(
            cyclePosition = 6,
            status = QuestionOccurrenceStatus.SCHEDULED,
            plannedAt = 6000L,
        )

        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = answeredWithAnswer,
                text = "Only real answer",
                createdAtEpochMillis = 1100L,
            ),
        )

        val entries = repository.observeEntries().first()
        assertEquals(1, entries.size)
        assertEquals(answerId, entries.single().answerId)
        assertEquals("Only real answer", entries.single().answerText)
    }

    @Test
    fun deleteAnswerRemovesRowButKeepsAnsweredOccurrence() = runBlocking {
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 1000L,
            completedAt = 1100L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "To delete",
                createdAtEpochMillis = 1100L,
            ),
        )

        assertEquals(1, repository.observeEntries().first().size)

        database.answerDao().deleteById(answerId)

        assertTrue(repository.observeEntries().first().isEmpty())
        val occurrence = database.questionOccurrenceDao().getById(occurrenceId)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occurrence.status)
    }

    @Test
    fun reactiveInsertAndDeleteUpdatesFlow() = runBlocking {
        seedPracticeStarted()
        val flow = repository.observeEntries()

        assertTrue(flow.first().isEmpty())

        val occurrenceId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 1000L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Reactive answer",
                createdAtEpochMillis = 1200L,
            ),
        )

        val afterInsert = flow.first { it.size == 1 }.single()
        assertEquals(answerId, afterInsert.answerId)
        assertEquals("Reactive answer", afterInsert.answerText)

        database.answerDao().deleteById(answerId)

        assertTrue(flow.first { it.isEmpty() }.isEmpty())
    }

    @Test
    fun reactiveAnswerTextUpdate() = runBlocking {
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 1000L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Before",
                createdAtEpochMillis = 1200L,
            ),
        )
        repository.observeEntries().first { it.single().answerText == "Before" }

        database.openHelper.writableDatabase.execSQL(
            "UPDATE answers SET text = 'After' WHERE id = $answerId",
        )

        val updated = repository.observeEntries().first { it.single().answerText == "After" }.single()
        assertEquals(answerId, updated.answerId)
        assertEquals("After", updated.answerText)
    }

    @Test
    fun unrelatedOccurrenceChangeDoesNotCreateArchiveRow() = runBlocking {
        seedPracticeStarted()
        val scheduledId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.SCHEDULED,
            plannedAt = 1000L,
        )

        assertTrue(repository.observeEntries().first().isEmpty())

        database.questionOccurrenceDao().update(
            database.questionOccurrenceDao().getById(scheduledId)!!.copy(
                status = QuestionOccurrenceStatus.AVAILABLE,
                openedAtEpochMillis = 1050L,
            ),
        )

        assertTrue(repository.observeEntries().first().isEmpty())
    }

    @Test
    fun archiveUsesQuestionTextSnapshotNotLiveQuestionEntity() = runBlocking {
        database.questionDao().insertAll(
            listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Live question text")),
        )
        database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        seedPracticeStarted()

        val occurrenceId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            questionTextSnapshot = "Frozen snapshot text",
            plannedAt = 1000L,
        )
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Answer",
                createdAtEpochMillis = 1100L,
            ),
        )

        database.questionDao().updateText(id = 1, text = "Updated live text")

        val entry = repository.observeEntries().first().single()
        assertEquals("Frozen snapshot text", entry.questionText)
        assertNotEquals("Updated live text", entry.questionText)
        assertNotEquals("Live question text", entry.questionText)
    }

    @Test
    fun archiveEntryIsNotRoomEntity() = runBlocking {
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            plannedAt = 1000L,
        )
        database.answerDao().insert(
            AnswerEntity(occurrenceId = occurrenceId, text = "X", createdAtEpochMillis = 1100L),
        )

        val entry = repository.observeEntries().first().single()
        assertEquals("ArchiveEntry", entry::class.simpleName)
        assertNotEquals("AnswerArchiveRow", entry::class.simpleName)
    }

    private suspend fun seedPracticeStarted() {
        if (database.questionDao().getById(1) == null) {
            database.questionDao().insertAll(
                listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Question 1")),
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
        } else {
            database.practiceStateDao().update(
                database.practiceStateDao().get()!!.copy(isPracticeStarted = true),
            )
        }
    }

    private suspend fun insertOccurrence(
        cyclePosition: Int,
        status: QuestionOccurrenceStatus,
        questionTextSnapshot: String = "Question 1",
        plannedAt: Long = 1000L + cyclePosition,
        availableUntil: Long = plannedAt + 1000L,
        completedAt: Long? = null,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = questionTextSnapshot,
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                completedAtEpochMillis = completedAt,
                status = status,
                zoneId = "UTC",
            ),
        )
    }
}
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik END
