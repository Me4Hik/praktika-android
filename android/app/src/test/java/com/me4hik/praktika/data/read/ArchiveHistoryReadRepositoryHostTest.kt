// PROMPT 115 — host tests for mixed archive history read model
package com.me4hik.praktika.data.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.DeferEventEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ArchiveHistoryReadRepositoryHostTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomArchiveReadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomArchiveReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun missOnlyQuestion_historyContainsMissed() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAt = 2_000L,
            questionTextSnapshot = "Missed Q",
        )

        val history = repository.observeHistoryForQuestion(1).first()
        assertEquals(1, history.size)
        val event = history.single() as ArchiveHistoryEvent.Missed
        assertEquals("o:$occurrenceId:missed", event.stableKey)
        assertEquals(2_000L, event.eventAtEpochMillis)
        assertEquals("Missed Q", event.questionTextSnapshot)
        assertTrue(repository.observeEntries().first().isEmpty())
    }

    @Test
    fun skipOnlyQuestion_historyContainsRejected() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 2,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            completedAt = 3_000L,
            questionTextSnapshot = "Skipped Q",
        )

        val history = repository.observeHistoryForQuestion(2).first()
        assertEquals(1, history.size)
        val event = history.single() as ArchiveHistoryEvent.Rejected
        assertEquals("o:$occurrenceId:rejected", event.stableKey)
        assertEquals(3_000L, event.eventAtEpochMillis)
    }

    @Test
    fun deferOnlyAvailableOccurrence_historyContainsDeferred() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 3,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
            questionTextSnapshot = "Live Q",
        )
        val deferId = database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 3,
                occurredAtEpochMillis = 1_500L,
                deferredUntilEpochMillis = 1_500L + 15 * 60_000L,
                durationMinutes = 15,
                zoneId = ZONE,
            ),
        )

        val history = repository.observeHistoryForQuestion(3).first()
        assertEquals(1, history.size)
        val event = history.single() as ArchiveHistoryEvent.Deferred
        assertEquals("d:$deferId", event.stableKey)
        assertEquals(15, event.durationMinutes)
        assertEquals(1_500L, event.eventAtEpochMillis)
        assertEquals(occurrenceId, event.occurrenceId)
        assertTrue(repository.observeEntriesForQuestion(3).first().isEmpty())
    }

    @Test
    fun twoDeferThenAnswer_threeEventsInOrder() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 4,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 5_000L,
            questionTextSnapshot = "Q4",
        )
        val deferOne = database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 4,
                occurredAtEpochMillis = 1_000L,
                deferredUntilEpochMillis = 1_900L,
                durationMinutes = 15,
                zoneId = ZONE,
            ),
        )
        val deferTwo = database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 4,
                occurredAtEpochMillis = 2_000L,
                deferredUntilEpochMillis = 2_300L,
                durationMinutes = 5,
                zoneId = ZONE,
            ),
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Final answer",
                createdAtEpochMillis = 5_000L,
            ),
        )

        val history = repository.observeHistoryForQuestion(4).first()
        assertEquals(3, history.size)
        assertEquals("d:$deferOne", history[0].stableKey)
        assertEquals("d:$deferTwo", history[1].stableKey)
        val answer = history[2] as ArchiveHistoryEvent.Answer
        assertEquals("a:$answerId", answer.stableKey)
        assertEquals("Final answer", answer.answerText)
        assertEquals(answerId, answer.answerId)
    }

    @Test
    fun answeredWithoutAnswerRow_keepsAnswerEvent() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 5,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 7_000L,
            questionTextSnapshot = "Deleted text Q",
        )

        val history = repository.observeHistoryForQuestion(5).first()
        assertEquals(1, history.size)
        val event = history.single() as ArchiveHistoryEvent.Answer
        assertEquals("o:$occurrenceId:answered", event.stableKey)
        assertNull(event.answerId)
        assertNull(event.answerText)
        assertEquals(7_000L, event.eventAtEpochMillis)
        assertTrue(repository.observeEntries().first().isEmpty())
    }

    @Test
    fun normalAnswer_keepsAnswerIdAndText() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 6,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 8_000L,
            questionTextSnapshot = "Normal Q",
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Keep me",
                createdAtEpochMillis = 8_100L,
            ),
        )

        val history = repository.observeHistoryForQuestion(6).first()
        val event = history.single() as ArchiveHistoryEvent.Answer
        assertEquals("a:$answerId", event.stableKey)
        assertEquals(answerId, event.answerId)
        assertEquals("Keep me", event.answerText)
        assertEquals(8_100L, event.eventAtEpochMillis)

        val answerOnly = repository.observeEntries().first().single()
        assertEquals(answerId, answerOnly.answerId)
        assertEquals("Keep me", answerOnly.answerText)
    }

    @Test
    fun sameTimestamp_deferredBeforeTerminal_deterministic() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 7,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            completedAt = 9_000L,
            questionTextSnapshot = "Tie Q",
        )
        val deferId = database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 7,
                occurredAtEpochMillis = 9_000L,
                deferredUntilEpochMillis = 9_000L + 60_000L,
                durationMinutes = 1,
                zoneId = ZONE,
            ),
        )

        val history = repository.observeHistoryForQuestion(7).first()
        assertEquals(2, history.size)
        assertEquals("d:$deferId", history[0].stableKey)
        assertEquals("o:$occurrenceId:rejected", history[1].stableKey)
    }

    @Test
    fun allHistoryFeed_includesTerminalAndDeferWithoutAnswers() = runBlocking {
        seedBase()
        insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAt = 10_000L,
        )
        val availableId = insertOccurrence(
            questionId = 2,
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
        )
        database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = availableId,
                questionId = 2,
                occurredAtEpochMillis = 11_000L,
                deferredUntilEpochMillis = 12_000L,
                durationMinutes = 10,
                zoneId = ZONE,
            ),
        )
        // Quiet question 3 has neither terminal nor defer — must stay out of feed.
        insertOccurrence(
            questionId = 3,
            cyclePosition = 3,
            status = QuestionOccurrenceStatus.SCHEDULED,
            completedAt = null,
        )

        val all = repository.observeAllHistoryEvents().first()
        val questionIds = all.map { it.questionId }.toSet()
        assertEquals(setOf(1, 2), questionIds)
        assertTrue(all.any { it is ArchiveHistoryEvent.Missed && it.questionId == 1 })
        assertTrue(all.any { it is ArchiveHistoryEvent.Deferred && it.questionId == 2 })
        assertTrue(repository.observeEntries().first().isEmpty())
    }

    @Test
    fun existingAnswerOnlyQueries_unchangedByMixedHistoryData() = runBlocking {
        seedBase()
        val answeredId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 20_000L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = answeredId,
                text = "Only answer row",
                createdAtEpochMillis = 20_000L,
            ),
        )
        insertOccurrence(
            questionId = 2,
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAt = 21_000L,
        )

        val entries = repository.observeEntries().first()
        assertEquals(1, entries.size)
        assertEquals(answerId, entries.single().answerId)

        val forQuestion = repository.observeEntriesForQuestion(1).first()
        assertEquals(1, forQuestion.size)
        assertEquals(answerId, forQuestion.single().answerId)

        val inRange = repository.observeEntriesInRange(19_000L, 22_000L).first()
        assertEquals(1, inRange.size)
        assertEquals(answerId, inRange.single().answerId)
    }

    private suspend fun seedBase() {
        database.questionDao().insertAll(
            (1..7).map { id ->
                QuestionEntity(id = id, cyclePosition = id, text = "Question $id", isActive = true)
            },
        )
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
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = 100L,
                currentCycleNumber = 1,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = 100L,
                pausedAtEpochMillis = null,
                activeZoneId = ZONE,
                seedVersion = 1,
            ),
        )
    }

    private suspend fun insertOccurrence(
        questionId: Int,
        cyclePosition: Int,
        status: QuestionOccurrenceStatus,
        completedAt: Long?,
        questionTextSnapshot: String = "Snapshot $questionId",
        plannedAt: Long = 1_000L,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = questionTextSnapshot,
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = plannedAt + 3_600_000L,
                completedAtEpochMillis = completedAt,
                status = status,
                zoneId = ZONE,
            ),
        )
    }

    private companion object {
        const val ZONE = "Europe/Kyiv"
    }
}
