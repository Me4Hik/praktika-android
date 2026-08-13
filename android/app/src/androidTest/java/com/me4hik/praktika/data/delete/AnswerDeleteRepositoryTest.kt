// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - instrumented tests AnswerDeleteRepository
package com.me4hik.praktika.data.delete

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
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.ui.archive.ArchiveDateGrouping
import com.me4hik.praktika.ui.archive.ArchiveQuestionGrouping
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnswerDeleteRepositoryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var deleteRepository: RoomAnswerDeleteRepository
    private lateinit var archiveRepository: RoomArchiveReadRepository
    private val zone = ZoneId.of("UTC")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        deleteRepository = RoomAnswerDeleteRepository(database, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
        archiveRepository = RoomArchiveReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun deleteRemovesAnswerPreservesAnsweredOccurrenceAndPracticeState() = runBlocking {
        val practiceState = seedPracticeStarted(nextCyclePosition = 4)
        val occurrenceId = insertOccurrence(
            cyclePosition = 3,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAtEpochMillis = 1500L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Delete me",
                createdAtEpochMillis = 1500L,
            ),
        )

        deleteRepository.deleteAnswer(answerId)

        assertNull(database.answerDao().getById(answerId))
        val occurrence = database.questionOccurrenceDao().getById(occurrenceId)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occurrence.status)
        val stateAfter = database.practiceStateDao().get()!!
        assertEquals(practiceState.nextCyclePosition, stateAfter.nextCyclePosition)
        assertEquals(practiceState.currentCycleNumber, stateAfter.currentCycleNumber)
        assertEquals(practiceState.lastProcessedAtEpochMillis, stateAfter.lastProcessedAtEpochMillis)
        assertEquals(practiceState.practiceStartedAtEpochMillis, stateAfter.practiceStartedAtEpochMillis)
    }

    @Test
    fun deleteSelectedAnswerOnly() = runBlocking {
        seedPracticeStarted()
        val occurrenceA = insertOccurrence(cyclePosition = 1, status = QuestionOccurrenceStatus.ANSWERED)
        val occurrenceB = insertOccurrence(cyclePosition = 2, status = QuestionOccurrenceStatus.ANSWERED, plannedAt = 2000L)
        val occurrenceC = insertOccurrence(cyclePosition = 3, status = QuestionOccurrenceStatus.ANSWERED, plannedAt = 3000L)
        val answerA = insertAnswer(occurrenceA, "A", 1100L)
        val answerB = insertAnswer(occurrenceB, "B", 1200L)
        val answerC = insertAnswer(occurrenceC, "C", 1300L)

        deleteRepository.deleteAnswer(answerB)

        assertEquals("A", database.answerDao().getById(answerA)!!.text)
        assertNull(database.answerDao().getById(answerB))
        assertEquals("C", database.answerDao().getById(answerC)!!.text)
    }

    @Test
    fun unknownAnswerIdIsSafe() = runBlocking {
        seedPracticeStarted()
        val occurrenceId = insertOccurrence(cyclePosition = 1, status = QuestionOccurrenceStatus.ANSWERED)
        val answerId = insertAnswer(occurrenceId, "Keep", 1100L)

        deleteRepository.deleteAnswer(999_999L)

        assertEquals("Keep", database.answerDao().getById(answerId)!!.text)
        assertEquals(QuestionOccurrenceStatus.ANSWERED, database.questionOccurrenceDao().getById(occurrenceId)!!.status)
    }

    @Test
    fun deleteLastAnswerRemovesDateAndQuestionGroupsReactively() = runBlocking {
        seedPracticeStarted()
        val occurrenceOne = insertOccurrence(
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            questionId = 1,
            plannedAt = 1000L,
        )
        val occurrenceTwo = insertOccurrence(
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.ANSWERED,
            questionId = 1,
            plannedAt = 2000L,
        )
        val answerOne = insertAnswer(occurrenceOne, "Only day one", 1_500L)
        insertAnswer(occurrenceTwo, "Other question same day", 1_800L)

        val dayFlow = archiveRepository.observeEntriesInRange(1_000L, 2_000L)
        assertEquals(2, dayFlow.first().size)
        deleteRepository.deleteAnswer(answerOne)
        assertEquals(1, dayFlow.first { it.size == 1 }.size)

        val allEntries = archiveRepository.observeEntries().first()
        assertEquals(1, allEntries.size)
        val groupedDates = ArchiveDateGrouping.groupDates(allEntries, zone)
        assertEquals(1, groupedDates.size)
        assertEquals(1, groupedDates.single().answerCount)

        val groupedQuestions = ArchiveQuestionGrouping.groupQuestions(allEntries)
        assertEquals(1, groupedQuestions.size)
        assertEquals(1, groupedQuestions.single().answerCount)

        deleteRepository.deleteAnswer(allEntries.single().answerId)
        assertTrue(archiveRepository.observeEntriesForQuestion(1).first().isEmpty())
        assertTrue(archiveRepository.observeEntries().first().isEmpty())
        assertTrue(ArchiveQuestionGrouping.groupQuestions(archiveRepository.observeEntries().first()).isEmpty())
    }

    private suspend fun seedPracticeStarted(nextCyclePosition: Int = 2): PracticeStateEntity {
        database.questionDao().insertAll(
            listOf(
                QuestionEntity(id = 1, cyclePosition = 1, text = "Question 1"),
                QuestionEntity(id = 2, cyclePosition = 2, text = "Question 2"),
            ),
        )
        database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        val state = PracticeStateEntity(
            id = 1,
            isPracticeStarted = true,
            isPaused = false,
            practiceStartedAtEpochMillis = 100L,
            currentCycleNumber = 1,
            nextCyclePosition = nextCyclePosition,
            lastProcessedAtEpochMillis = 100L,
            pausedAtEpochMillis = null,
            activeZoneId = "UTC",
            seedVersion = 1,
        )
        database.practiceStateDao().insert(state)
        return state
    }

    private suspend fun insertOccurrence(
        cyclePosition: Int,
        status: QuestionOccurrenceStatus,
        questionId: Int = 1,
        plannedAt: Long = 1000L + cyclePosition,
        completedAtEpochMillis: Long? = plannedAt + 100L,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = "Snapshot $cyclePosition",
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = plannedAt + 1000L,
                completedAtEpochMillis = completedAtEpochMillis,
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
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
