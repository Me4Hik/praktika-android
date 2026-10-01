// PROMPT 115 — host tests for mixed archive history read model
// 01.10.2026 Archive T1 incomplete defer filter cursor by Me4Hik
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - occurrence unit contract
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
    fun missOnlyQuestion_historyContainsMissedUnit() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAt = 2_000L,
            questionTextSnapshot = "Missed Q",
        )

        val history = repository.observeOccurrenceHistoryForQuestion(1).first()
        assertEquals(1, history.size)
        val unit = history.single()
        assertEquals(ArchiveOccurrenceOutcome.MISSED, unit.outcome)
        assertEquals("o:$occurrenceId:missed", unit.stableKey)
        assertEquals(2_000L, unit.eventAtEpochMillis)
        assertEquals("Missed Q", unit.questionTextSnapshot)
        assertEquals(0, unit.deferCount)
        assertTrue(repository.observeEntries().first().isEmpty())
    }

    @Test
    fun skipOnlyQuestion_historyContainsRejectedUnit() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 2,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            completedAt = 3_000L,
            questionTextSnapshot = "Skipped Q",
        )

        val history = repository.observeOccurrenceHistoryForQuestion(2).first()
        assertEquals(1, history.size)
        val unit = history.single()
        assertEquals(ArchiveOccurrenceOutcome.REJECTED, unit.outcome)
        assertEquals("o:$occurrenceId:rejected", unit.stableKey)
        assertEquals(3_000L, unit.eventAtEpochMillis)
        assertEquals(0, unit.deferCount)
    }

    @Test
    fun deferOnlyAvailableOccurrence_historyIsEmpty() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 3,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
            questionTextSnapshot = "Live Q",
        )
        database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 3,
                occurredAtEpochMillis = 1_500L,
                deferredUntilEpochMillis = 1_500L + 15 * 60_000L,
                durationMinutes = 15,
                zoneId = ZONE,
            ),
        )

        assertTrue(repository.observeOccurrenceHistoryForQuestion(3).first().isEmpty())
        assertTrue(repository.observeAllOccurrenceHistory().first().isEmpty())
        assertTrue(repository.observeEntriesForQuestion(3).first().isEmpty())
    }

    @Test
    fun fiveDeferThenAnswer_oneUnitWithDeferCountFive() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 4,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 10_000L,
            questionTextSnapshot = "Contract Q",
        )
        repeat(5) { index ->
            database.deferEventDao().insert(
                DeferEventEntity(
                    occurrenceId = occurrenceId,
                    questionId = 4,
                    occurredAtEpochMillis = 1_000L + index,
                    deferredUntilEpochMillis = 2_000L + index,
                    durationMinutes = 5,
                    zoneId = ZONE,
                ),
            )
        }
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "Final after five",
                createdAtEpochMillis = 10_000L,
            ),
        )

        val history = repository.observeOccurrenceHistoryForQuestion(4).first()
        assertEquals(1, history.size)
        val unit = history.single()
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
        assertEquals(5, unit.deferCount)
        assertEquals(5, unit.deferEvents.size)
        assertEquals(occurrenceId, unit.occurrenceId)
    }

    @Test
    // 01.10.2026 Archive Acceptance B cursor by Me4Hik START - 5 occurrences × defer×1 vs A (1×defer×5)
    fun fiveOccurrencesEachDeferOnceThenAnswer_fiveUnitsDeferCountOne() = runBlocking {
        // Same raw defer count as fiveDeferThenAnswer_oneUnitWithDeferCountFive (5),
        // but Archive must show 5 units with deferCount=1 each — not 1 unit with deferCount=5.
        seedBase()
        val questionId = 1
        val occurrenceIds = ArrayList<Long>(5)
        repeat(5) { index ->
            val completedAt = 10_000L + index * 1_000L
            val occurrenceId = insertOccurrence(
                questionId = questionId,
                cyclePosition = index + 1,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAt = completedAt,
                questionTextSnapshot = "Q1 occurrence ${index + 1}",
                plannedAt = 1_000L + index,
            )
            occurrenceIds += occurrenceId
            database.deferEventDao().insert(
                DeferEventEntity(
                    occurrenceId = occurrenceId,
                    questionId = questionId,
                    occurredAtEpochMillis = completedAt - 500L,
                    deferredUntilEpochMillis = completedAt - 100L,
                    durationMinutes = 5,
                    zoneId = ZONE,
                ),
            )
            database.answerDao().insert(
                AnswerEntity(
                    occurrenceId = occurrenceId,
                    text = "Answer ${index + 1}",
                    createdAtEpochMillis = completedAt,
                ),
            )
        }

        val history = repository.observeOccurrenceHistoryForQuestion(questionId).first()
        assertEquals(5, history.size)
        assertEquals(5, history.map { it.occurrenceId }.toSet().size)
        assertEquals(occurrenceIds, history.map { it.occurrenceId })
        history.forEach { unit ->
            assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
            assertEquals(1, unit.deferCount)
            assertEquals(1, unit.deferEvents.size)
        }
        for (i in 1 until history.size) {
            assertTrue(history[i - 1].eventAtEpochMillis < history[i].eventAtEpochMillis)
        }
    }
    // 01.10.2026 Archive Acceptance B cursor by Me4Hik END

    @Test
    fun completedWithoutDefer_oneUnitDeferCountZero() = runBlocking {
        seedBase()
        insertOccurrence(
            questionId = 5,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 7_000L,
            questionTextSnapshot = "No defer Q",
        )

        val unit = repository.observeOccurrenceHistoryForQuestion(5).first().single()
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
        assertEquals(0, unit.deferCount)
        assertTrue(unit.deferEvents.isEmpty())
    }

    @Test
    fun deferThenAnswer_oneUnitWithNestedDefer() = runBlocking {
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
        assertTrue(repository.observeOccurrenceHistoryForQuestion(3).first().isEmpty())

        database.questionOccurrenceDao().updateStatusAndCompletion(
            id = occurrenceId,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAtEpochMillis = 5_000L,
        )
        val answerId = database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = "After defer",
                createdAtEpochMillis = 5_000L,
            ),
        )

        val history = repository.observeOccurrenceHistoryForQuestion(3).first()
        assertEquals(1, history.size)
        val unit = history.single()
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
        assertEquals("a:$answerId", unit.stableKey)
        assertEquals(1, unit.deferCount)
        assertEquals(deferId, unit.deferEvents.single().deferEventId)
    }

    @Test
    fun deferThenExplicitSkip_oneUnitWithDeferCount() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 3,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
            questionTextSnapshot = "Skip after defer",
        )
        val deferId = database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 3,
                occurredAtEpochMillis = 2_000L,
                deferredUntilEpochMillis = 2_000L + 10 * 60_000L,
                durationMinutes = 10,
                zoneId = ZONE,
            ),
        )
        assertTrue(repository.observeOccurrenceHistoryForQuestion(3).first().isEmpty())

        database.questionOccurrenceDao().updateStatusAndCompletion(
            id = occurrenceId,
            status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            completedAtEpochMillis = 4_000L,
        )

        val unit = repository.observeOccurrenceHistoryForQuestion(3).first().single()
        assertEquals(ArchiveOccurrenceOutcome.REJECTED, unit.outcome)
        assertEquals("o:$occurrenceId:rejected", unit.stableKey)
        assertEquals(1, unit.deferCount)
        assertEquals(deferId, unit.deferEvents.single().deferEventId)
    }

    @Test
    fun deferThenMissedByTime_oneUnitWithDeferCount() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 3,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
            questionTextSnapshot = "Miss after defer",
        )
        val deferId = database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 3,
                occurredAtEpochMillis = 2_500L,
                deferredUntilEpochMillis = 2_500L + 5 * 60_000L,
                durationMinutes = 5,
                zoneId = ZONE,
            ),
        )
        assertTrue(repository.observeOccurrenceHistoryForQuestion(3).first().isEmpty())

        database.questionOccurrenceDao().updateStatusAndCompletion(
            id = occurrenceId,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAtEpochMillis = 6_000L,
        )

        val unit = repository.observeOccurrenceHistoryForQuestion(3).first().single()
        assertEquals(ArchiveOccurrenceOutcome.MISSED, unit.outcome)
        assertEquals("o:$occurrenceId:missed", unit.stableKey)
        assertEquals(1, unit.deferCount)
        assertEquals(deferId, unit.deferEvents.single().deferEventId)
    }

    @Test
    fun multipleOccurrencesSameQuestion_oneUnitEach_orderedByTerminalTimestamp() = runBlocking {
        seedBase()
        val firstId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAt = 3_000L,
            questionTextSnapshot = "First",
        )
        val secondId = insertOccurrence(
            questionId = 1,
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 8_000L,
            questionTextSnapshot = "Second",
        )
        database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = secondId,
                questionId = 1,
                occurredAtEpochMillis = 5_000L,
                deferredUntilEpochMillis = 6_000L,
                durationMinutes = 10,
                zoneId = ZONE,
            ),
        )
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = secondId,
                text = "Later answer",
                createdAtEpochMillis = 8_000L,
            ),
        )

        val history = repository.observeOccurrenceHistoryForQuestion(1).first()
        assertEquals(2, history.size)
        assertEquals(firstId, history[0].occurrenceId)
        assertEquals(ArchiveOccurrenceOutcome.MISSED, history[0].outcome)
        assertEquals(0, history[0].deferCount)
        assertEquals(secondId, history[1].occurrenceId)
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, history[1].outcome)
        assertEquals(1, history[1].deferCount)
        assertTrue(history[0].eventAtEpochMillis < history[1].eventAtEpochMillis)
    }

    @Test
    fun twoDeferThenAnswer_oneUnitNotThreeEvents() = runBlocking {
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

        val history = repository.observeOccurrenceHistoryForQuestion(4).first()
        assertEquals(1, history.size)
        val unit = history.single()
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
        assertEquals("a:$answerId", unit.stableKey)
        assertEquals("Final answer", unit.answerText)
        assertEquals(answerId, unit.answerId)
        assertEquals(2, unit.deferCount)
        assertEquals(listOf(deferOne, deferTwo), unit.deferEvents.map { it.deferEventId })
    }

    @Test
    fun answeredWithoutAnswerRow_keepsAnsweredUnit() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 5,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = 7_000L,
            questionTextSnapshot = "Deleted text Q",
        )

        val unit = repository.observeOccurrenceHistoryForQuestion(5).first().single()
        assertEquals(ArchiveOccurrenceOutcome.ANSWERED, unit.outcome)
        assertEquals("o:$occurrenceId:answered", unit.stableKey)
        assertNull(unit.answerId)
        assertNull(unit.answerText)
        assertEquals(7_000L, unit.eventAtEpochMillis)
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

        val unit = repository.observeOccurrenceHistoryForQuestion(6).first().single()
        assertEquals("a:$answerId", unit.stableKey)
        assertEquals(answerId, unit.answerId)
        assertEquals("Keep me", unit.answerText)
        assertEquals(8_100L, unit.eventAtEpochMillis)

        val answerOnly = repository.observeEntries().first().single()
        assertEquals(answerId, answerOnly.answerId)
        assertEquals("Keep me", answerOnly.answerText)
    }

    @Test
    fun skipWithSameTimestampDefer_stillOneRejectedUnit() = runBlocking {
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

        val unit = repository.observeOccurrenceHistoryForQuestion(7).first().single()
        assertEquals(ArchiveOccurrenceOutcome.REJECTED, unit.outcome)
        assertEquals("o:$occurrenceId:rejected", unit.stableKey)
        assertEquals(1, unit.deferCount)
        assertEquals(deferId, unit.deferEvents.single().deferEventId)
    }

    @Test
    fun allHistoryFeed_includesTerminalOnly_excludesDeferOnAvailable() = runBlocking {
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
        insertOccurrence(
            questionId = 3,
            cyclePosition = 3,
            status = QuestionOccurrenceStatus.SCHEDULED,
            completedAt = null,
        )

        val all = repository.observeAllOccurrenceHistory().first()
        assertEquals(setOf(1), all.map { it.questionId }.toSet())
        assertEquals(ArchiveOccurrenceOutcome.MISSED, all.single().outcome)
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
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
