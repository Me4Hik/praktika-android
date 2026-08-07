// 04.08.2026 DB Refactoring cursor by Me4Hik START - instrumented-тесты Room базы
package com.me4hik.praktika.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.converter.RoomConverters
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PraktikaDatabaseTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var questionDao: com.me4hik.praktika.data.local.dao.QuestionDao
    private lateinit var scheduleSlotDao: com.me4hik.praktika.data.local.dao.ScheduleSlotDao
    private lateinit var questionOccurrenceDao: com.me4hik.praktika.data.local.dao.QuestionOccurrenceDao
    private lateinit var answerDao: com.me4hik.praktika.data.local.dao.AnswerDao
    private lateinit var practiceStateDao: com.me4hik.praktika.data.local.dao.PracticeStateDao

    private var persistentDatabaseFile: File? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PraktikaDatabase::class.java,
        ).build()
        questionDao = database.questionDao()
        scheduleSlotDao = database.scheduleSlotDao()
        questionOccurrenceDao = database.questionOccurrenceDao()
        answerDao = database.answerDao()
        practiceStateDao = database.practiceStateDao()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        persistentDatabaseFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        persistentDatabaseFile = null
    }

    @Test
    fun test1_databaseCreatesAndCloses() = runBlocking {
        assertNotNull(questionDao)
        assertNotNull(scheduleSlotDao)
        assertNotNull(questionOccurrenceDao)
        assertNotNull(answerDao)
        assertNotNull(practiceStateDao)
        database.close()
    }

    @Test
    fun test2_questionsSortedByCyclePosition() = runBlocking {
        questionDao.insertAll(
            listOf(
                QuestionEntity(id = 3, cyclePosition = 3, text = "Question C"),
                QuestionEntity(id = 1, cyclePosition = 1, text = "Question A"),
                QuestionEntity(id = 2, cyclePosition = 2, text = "Question B"),
            ),
        )

        val ordered = questionDao.getAllOrderedByCyclePosition()

        assertEquals(listOf(1, 2, 3), ordered.map { it.cyclePosition })
        assertEquals(listOf("Question A", "Question B", "Question C"), ordered.map { it.text })
    }

    @Test
    fun test3_uniqueQuestionCyclePosition() = runBlocking {
        questionDao.insertAll(
            listOf(
                QuestionEntity(id = 1, cyclePosition = 1, text = "First"),
            ),
        )

        try {
            questionDao.insertAll(
                listOf(
                    QuestionEntity(id = 2, cyclePosition = 1, text = "Duplicate position"),
                ),
            )
            fail("Expected SQLiteConstraintException for duplicate cyclePosition")
        } catch (exception: SQLiteConstraintException) {
            assertTrue(exception.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun test4_uniqueScheduleSlotTimeOfDayMinutes() = runBlocking {
        scheduleSlotDao.insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
            ),
        )

        try {
            scheduleSlotDao.insertAll(
                listOf(
                    ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 660),
                ),
            )
            fail("Expected SQLiteConstraintException for duplicate timeOfDayMinutes")
        } catch (exception: SQLiteConstraintException) {
            assertTrue(exception.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun test5_createAndReadQuestionOccurrence() = runBlocking {
        questionDao.insertAll(
            listOf(
                QuestionEntity(id = 1, cyclePosition = 1, text = "Test question"),
            ),
        )
        scheduleSlotDao.insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
            ),
        )

        val occurrence = QuestionOccurrenceEntity(
            questionId = 1,
            questionTextSnapshot = "Test question",
            cycleNumber = 1,
            cyclePosition = 1,
            scheduleSlotIndex = 2,
            plannedAtEpochMillis = 1_700_000_000_000L,
            availableUntilEpochMillis = 1_700_014_400_000L,
            openedAtEpochMillis = null,
            completedAtEpochMillis = null,
            status = QuestionOccurrenceStatus.SCHEDULED,
            zoneId = "Europe/Moscow",
        )
        questionOccurrenceDao.insert(occurrence)

        val stored = questionOccurrenceDao.getByCycleAndPosition(cycleNumber = 1, cyclePosition = 1)

        assertNotNull(stored)
        assertEquals(1, stored!!.questionId)
        assertEquals(1, stored.cycleNumber)
        assertEquals(1, stored.cyclePosition)
        assertEquals(2, stored.scheduleSlotIndex)
        assertEquals(1_700_000_000_000L, stored.plannedAtEpochMillis)
        assertEquals(1_700_014_400_000L, stored.availableUntilEpochMillis)
        assertNull(stored.openedAtEpochMillis)
        assertNull(stored.completedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, stored.status)
        assertEquals("Europe/Moscow", stored.zoneId)
        assertEquals("Test question", stored.questionTextSnapshot)
    }

    @Test
    fun test6_uniqueOccurrenceCyclePositionWithinCycle() = runBlocking {
        questionDao.insertAll(listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Q1")))
        scheduleSlotDao.insertAll(listOf(ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660)))

        questionOccurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Q1",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 100L,
                availableUntilEpochMillis = 200L,
                status = QuestionOccurrenceStatus.SCHEDULED,
                zoneId = "UTC",
            ),
        )

        try {
            questionOccurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = 1,
                    questionTextSnapshot = "Q1",
                    cycleNumber = 1,
                    cyclePosition = 1,
                    scheduleSlotIndex = 1,
                    plannedAtEpochMillis = 300L,
                    availableUntilEpochMillis = 400L,
                    status = QuestionOccurrenceStatus.SCHEDULED,
                    zoneId = "UTC",
                ),
            )
            fail("Expected SQLiteConstraintException for duplicate cycleNumber/cyclePosition")
        } catch (exception: SQLiteConstraintException) {
            assertTrue(exception.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun test7_oneAnswerPerOccurrence() = runBlocking {
        prepareOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)
        val occurrenceId = questionOccurrenceDao.getByCycleAndPosition(1, 1)!!.id

        answerDao.insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "First answer",
                createdAtEpochMillis = 500L,
            ),
        )

        try {
            answerDao.insert(
                AnswerEntity(
                    occurrenceId = occurrenceId,
                    text = "Second answer",
                    createdAtEpochMillis = 600L,
                ),
            )
            fail("Expected SQLiteConstraintException for duplicate occurrenceId")
        } catch (exception: SQLiteConstraintException) {
            assertTrue(exception.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun test8_physicalDeleteAnswerKeepsAnsweredOccurrence() = runBlocking {
        prepareOccurrence(status = QuestionOccurrenceStatus.ANSWERED)
        val occurrenceId = questionOccurrenceDao.getByCycleAndPosition(1, 1)!!.id

        val answerId = answerDao.insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Answer to delete",
                createdAtEpochMillis = 700L,
            ),
        )

        answerDao.deleteById(answerId)

        assertNull(answerDao.getByOccurrenceId(occurrenceId))
        assertEquals(0, answerDao.count())

        val occurrence = questionOccurrenceDao.getById(occurrenceId)
        assertNotNull(occurrence)
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occurrence!!.status)
    }

    @Test
    fun test9_archiveJoinReturnsOnlySelectedQuestion() = runBlocking {
        questionDao.insertAll(
            listOf(
                QuestionEntity(id = 1, cyclePosition = 1, text = "Question one"),
                QuestionEntity(id = 2, cyclePosition = 2, text = "Question two"),
            ),
        )
        scheduleSlotDao.insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
            ),
        )

        val occurrenceOneId = questionOccurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Question one",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1000L,
                availableUntilEpochMillis = 2000L,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = "UTC",
            ),
        )
        val occurrenceTwoId = questionOccurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 2,
                questionTextSnapshot = "Question two",
                cycleNumber = 1,
                cyclePosition = 2,
                scheduleSlotIndex = 2,
                plannedAtEpochMillis = 3000L,
                availableUntilEpochMillis = 4000L,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = "UTC",
            ),
        )

        answerDao.insert(
            AnswerEntity(
                occurrenceId = occurrenceOneId,
                text = "Answer for question one",
                createdAtEpochMillis = 1100L,
            ),
        )
        answerDao.insert(
            AnswerEntity(
                occurrenceId = occurrenceTwoId,
                text = "Answer for question two",
                createdAtEpochMillis = 3100L,
            ),
        )

        val archiveRows = answerDao.getAllForQuestion(questionId = 1)

        assertEquals(1, archiveRows.size)
        val row = archiveRows.first()
        assertEquals(1, row.questionId)
        assertEquals("Question one", row.questionText)
        assertEquals(1, row.cycleNumber)
        assertEquals(1, row.cyclePosition)
        assertEquals("Answer for question one", row.answerText)
        assertEquals(1100L, row.createdAtEpochMillis)
        assertEquals(occurrenceOneId, row.occurrenceId)
    }

    @Test
    fun test10_practiceStateSingleRowWithoutOnboardingCompleted() = runBlocking {
        val initialState = PracticeStateEntity(
            id = 1,
            isPracticeStarted = false,
            isPaused = false,
            practiceStartedAtEpochMillis = null,
            currentCycleNumber = 0,
            nextCyclePosition = 1,
            lastProcessedAtEpochMillis = null,
            pausedAtEpochMillis = null,
            activeZoneId = "Europe/Moscow",
            seedVersion = 0,
        )

        practiceStateDao.insert(initialState)

        val loaded = practiceStateDao.get()
        assertNotNull(loaded)
        assertEquals(false, loaded!!.isPracticeStarted)

        practiceStateDao.update(loaded.copy(isPracticeStarted = true))

        val updated = practiceStateDao.get()
        assertNotNull(updated)
        assertEquals(true, updated!!.isPracticeStarted)
        assertEquals(1, updated.id)
    }

    @Test
    fun test11_enumConverterRoundTripForAllStatuses() = runBlocking {
        val converter = RoomConverters()
        val statuses = QuestionOccurrenceStatus.entries

        statuses.forEach { status ->
            val stored = converter.fromQuestionOccurrenceStatus(status)
            val restored = converter.toQuestionOccurrenceStatus(stored)
            assertEquals(status, restored)
        }

        questionDao.insertAll(listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Enum question")))
        scheduleSlotDao.insertAll(listOf(ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660)))

        statuses.forEachIndexed { index, status ->
            questionOccurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = 1,
                    questionTextSnapshot = "Enum question",
                    cycleNumber = 1,
                    cyclePosition = index + 1,
                    scheduleSlotIndex = 1,
                    plannedAtEpochMillis = 1000L + index,
                    availableUntilEpochMillis = 2000L + index,
                    status = status,
                    zoneId = "UTC",
                ),
            )
        }

        val storedOccurrences = questionOccurrenceDao.getAllForQuestion(questionId = 1)
        assertEquals(statuses.size, storedOccurrences.size)
        storedOccurrences.forEachIndexed { index, occurrence ->
            assertEquals(statuses[index], occurrence.status)
        }
    }

    @Test
    fun test12_closeAndReopenPersistentDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        persistentDatabaseFile = File(context.cacheDir, "praktika_stage2_test.db")
        persistentDatabaseFile!!.delete()

        val persistentDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            persistentDatabaseFile!!.absolutePath,
        ).build()

        val persistentQuestionDao = persistentDatabase.questionDao()
        persistentQuestionDao.insertAll(
            listOf(
                QuestionEntity(id = 10, cyclePosition = 10, text = "Persistent question"),
            ),
        )
        persistentDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            persistentDatabaseFile!!.absolutePath,
        ).build()

        val restored = reopenedDatabase.questionDao().getById(10)
        reopenedDatabase.close()

        assertNotNull(restored)
        assertEquals(10, restored!!.cyclePosition)
        assertEquals("Persistent question", restored.text)
    }

    @Test
    fun test13_archiveUsesQuestionTextSnapshotAfterQuestionUpdate() = runBlocking {
        questionDao.insertAll(
            listOf(
                QuestionEntity(id = 1, cyclePosition = 1, text = "Первоначальный вопрос"),
            ),
        )
        scheduleSlotDao.insertAll(listOf(ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660)))

        val occurrenceId = questionOccurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Первоначальный вопрос",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1000L,
                availableUntilEpochMillis = 2000L,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = "UTC",
            ),
        )

        answerDao.insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Ответ",
                createdAtEpochMillis = 1100L,
            ),
        )

        questionDao.updateText(id = 1, text = "Изменённый вопрос")

        val archiveRows = answerDao.getAllForQuestion(questionId = 1)
        assertEquals(1, archiveRows.size)
        assertEquals("Первоначальный вопрос", archiveRows.first().questionText)
    }

    private suspend fun prepareOccurrence(status: QuestionOccurrenceStatus) {
        questionDao.insertAll(listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Prepared question")))
        scheduleSlotDao.insertAll(listOf(ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660)))
        questionOccurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Prepared question",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 100L,
                availableUntilEpochMillis = 200L,
                status = status,
                zoneId = "UTC",
            ),
        )
    }
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
