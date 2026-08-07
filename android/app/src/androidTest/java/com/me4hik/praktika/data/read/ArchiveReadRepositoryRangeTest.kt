// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - range repository instrumented tests
package com.me4hik.praktika.data.read

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveReadRepositoryRangeTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomArchiveReadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomArchiveReadRepository(database)
        runBlocking {
            database.questionDao().insertAll(
                listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Question")),
            )
            database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun startInclusiveIncluded() = runBlocking {
        val occurrenceId = insertOccurrence(1)
        insertAnswer(occurrenceId, text = "At start", createdAt = 1_000L)
        val entries = repository.observeEntriesInRange(1_000L, 2_000L).first()
        assertEquals(1, entries.size)
        assertEquals("At start", entries.single().answerText)
    }

    @Test
    fun endExclusiveExcluded() = runBlocking {
        val occurrenceId = insertOccurrence(1)
        insertAnswer(occurrenceId, text = "At end", createdAt = 2_000L)
        val entries = repository.observeEntriesInRange(1_000L, 2_000L).first()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun millisecondBeforeEndIncluded() = runBlocking {
        val occurrenceId = insertOccurrence(1)
        insertAnswer(occurrenceId, text = "Before end", createdAt = 1_999L)
        val entries = repository.observeEntriesInRange(1_000L, 2_000L).first()
        assertEquals(1, entries.size)
    }

    @Test
    fun neighborRangeExcluded() = runBlocking {
        val firstOccurrence = insertOccurrence(1)
        val secondOccurrence = insertOccurrence(2)
        insertAnswer(firstOccurrence, text = "Day one", createdAt = 1_500L)
        insertAnswer(secondOccurrence, text = "Day two", createdAt = 3_500L)
        val entries = repository.observeEntriesInRange(1_000L, 2_000L).first()
        assertEquals(1, entries.size)
        assertEquals("Day one", entries.single().answerText)
    }

    @Test
    fun orderingNewestFirstWithAnswerIdTieBreaker() = runBlocking {
        val firstOccurrence = insertOccurrence(1)
        val secondOccurrence = insertOccurrence(2)
        val olderId = insertAnswer(firstOccurrence, text = "Older", createdAt = 1_500L)
        val newerId = insertAnswer(secondOccurrence, text = "Newer", createdAt = 1_800L)
        val sameTimeId = insertAnswer(insertOccurrence(3), text = "Same time", createdAt = 1_800L)
        val ordered = repository.observeEntriesInRange(1_000L, 2_000L).first().map { it.answerId }
        assertEquals(listOf(sameTimeId, newerId, olderId), ordered)
    }

    @Test
    fun deleteAnswerRemovesRangeRowReactively() = runBlocking {
        val occurrenceId = insertOccurrence(1)
        val answerId = insertAnswer(occurrenceId, text = "Delete me", createdAt = 1_500L)
        val flow = repository.observeEntriesInRange(1_000L, 2_000L)
        assertEquals(1, flow.first().size)
        database.answerDao().deleteById(answerId)
        assertTrue(flow.first { it.isEmpty() }.isEmpty())
    }

    private suspend fun insertOccurrence(cyclePosition: Int): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Snapshot $cyclePosition",
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 500L + cyclePosition,
                availableUntilEpochMillis = 900L + cyclePosition,
                status = QuestionOccurrenceStatus.ANSWERED,
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
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
