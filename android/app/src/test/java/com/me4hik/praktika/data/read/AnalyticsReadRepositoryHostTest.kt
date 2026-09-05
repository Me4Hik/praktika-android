// PROMPT 129 — host tests for analytics read repository projections
package com.me4hik.praktika.data.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.DeferEventEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.analytics.AnalyticsAggregator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AnalyticsReadRepositoryHostTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomAnalyticsReadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAnalyticsReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun getTerminalRows_includesZoneIdAndSkipsNonTerminal() = runBlocking {
        seedBase()
        insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAt = 2_000L,
            zoneId = "Europe/Kyiv",
        )
        insertOccurrence(
            questionId = 2,
            cyclePosition = 2,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
            zoneId = "Europe/Kyiv",
        )

        val rows = repository.getTerminalRows()
        assertEquals(1, rows.size)
        assertEquals("Europe/Kyiv", rows.single().zoneId)
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, rows.single().status)
    }

    @Test
    fun getDeferRows_includesStoredZoneId() = runBlocking {
        seedBase()
        val occurrenceId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
            completedAt = null,
            zoneId = "Asia/Tokyo",
        )
        database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 1,
                occurredAtEpochMillis = 1_500L,
                deferredUntilEpochMillis = 2_500L,
                durationMinutes = 15,
                zoneId = "Asia/Tokyo",
            ),
        )

        val rows = repository.getDeferRows()
        assertEquals(1, rows.size)
        assertEquals("Asia/Tokyo", rows.single().zoneId)
        assertEquals(15, rows.single().durationMinutes)
    }

    @Test
    fun repositoryRows_aggregateUsesStoredZones() = runBlocking {
        seedBase()
        val completedAt = LocalDateTime.of(2026, 9, 4, 23, 30)
            .atZone(ZoneId.of("Europe/Kyiv"))
            .toInstant()
            .toEpochMilli()
        val occurrenceId = insertOccurrence(
            questionId = 1,
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.ANSWERED,
            completedAt = completedAt,
            zoneId = "Europe/Kyiv",
        )
        database.deferEventDao().insert(
            DeferEventEntity(
                occurrenceId = occurrenceId,
                questionId = 1,
                occurredAtEpochMillis = completedAt - 60_000L,
                deferredUntilEpochMillis = completedAt,
                durationMinutes = 10,
                zoneId = "Europe/Kyiv",
            ),
        )

        val snapshot = AnalyticsAggregator.aggregate(
            repository.getTerminalRows(),
            repository.getDeferRows(),
        )
        val friday = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        assertEquals(1, friday.terminalCount)
        assertEquals(1, friday.deferredOccurrenceCount)
        assertEquals(1, friday.deferEventCount)
        assertTrue(friday.deferredOccurrenceRate <= 1.0)
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
                activeZoneId = "Europe/Kyiv",
                seedVersion = 1,
            ),
        )
    }

    private suspend fun insertOccurrence(
        questionId: Int,
        cyclePosition: Int,
        status: QuestionOccurrenceStatus,
        completedAt: Long?,
        zoneId: String,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = "Snapshot $questionId",
                cycleNumber = 1,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1_000L,
                availableUntilEpochMillis = 1_000L + 3_600_000L,
                completedAtEpochMillis = completedAt,
                status = status,
                zoneId = zoneId,
            ),
        )
    }
}
