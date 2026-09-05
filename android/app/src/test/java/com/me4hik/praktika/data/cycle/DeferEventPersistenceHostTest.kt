package com.me4hik.praktika.data.cycle

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DeferEventPersistenceHostTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV)
        repository = CycleRepository(database, timeProvider, NoOpBackupMutationRequestSink)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun successfulDefer_writesExactEvent() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))

        val result = repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 15)
        assertTrue(result is CycleResult.DeferCompleted)

        val events = database.deferEventDao().getForOccurrenceOrdered(occurrence.id)
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(occurrence.id, event.occurrenceId)
        assertEquals(occurrence.questionId, event.questionId)
        assertEquals(epochAt(11, 10, 0), event.occurredAtEpochMillis)
        assertEquals(epochAt(11, 25, 0), event.deferredUntilEpochMillis)
        assertEquals(15, event.durationMinutes)
        assertEquals(ZONE_KIEV, event.zoneId)
        assertEquals(epochAt(11, 25, 0), database.questionOccurrenceDao().getById(occurrence.id)!!.deferredUntilEpochMillis)
    }

    @Test
    fun reDefer_createsSecondEventForSameOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!

        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 15)
        timeProvider.setEpochMillis(epochAt(11, 12, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)

        val events = database.deferEventDao().getForOccurrenceOrdered(occurrence.id)
        assertEquals(2, events.size)
        assertEquals(occurrence.id, events[0].occurrenceId)
        assertEquals(occurrence.id, events[1].occurrenceId)
        assertEquals(15, events[0].durationMinutes)
        assertEquals(5, events[1].durationMinutes)
        assertEquals(epochAt(11, 10, 0), events[0].occurredAtEpochMillis)
        assertEquals(epochAt(11, 12, 0), events[1].occurredAtEpochMillis)
        assertEquals(epochAt(11, 25, 0), events[0].deferredUntilEpochMillis)
        assertEquals(epochAt(11, 17, 0), events[1].deferredUntilEpochMillis)
        assertEquals(epochAt(11, 17, 0), database.questionOccurrenceDao().getById(occurrence.id)!!.deferredUntilEpochMillis)
    }

    @Test
    fun staleDefer_doesNotWriteEvent() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))

        try {
            repository.deferAvailableOccurrence(occurrence.id + 999L, durationMinutes = 15)
            fail("Expected CycleDeferNotAllowedException")
        } catch (_: CycleDeferNotAllowedException) {
            // expected
        }

        assertEquals(0, database.deferEventDao().count())
        assertNull(database.questionOccurrenceDao().getById(occurrence.id)!!.deferredUntilEpochMillis)
    }

    @Test
    fun expiredWindowDefer_doesNotWriteEvent() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        // Past availableUntil (15:00) → reconcile marks MISSED; defer must fail without event.
        timeProvider.setEpochMillis(epochAt(15, 0, 0))

        try {
            repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 15)
            fail("Expected CycleDeferNotAllowedException")
        } catch (_: CycleDeferNotAllowedException) {
            // expected
        }

        assertEquals(0, database.deferEventDao().count())
    }

    @Test
    fun eventInsertFailure_rollsBackDeferredUntil() = runBlocking {
        database.close()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(
                object : androidx.room.RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TRIGGER IF NOT EXISTS fail_defer_event_insert
                            BEFORE INSERT ON defer_events
                            BEGIN
                              SELECT RAISE(ABORT, 'forced defer_event insert failure');
                            END
                            """.trimIndent(),
                        )
                    }
                },
            )
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV)
        repository = CycleRepository(database, timeProvider, NoOpBackupMutationRequestSink)

        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))

        try {
            repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 15)
            fail("Expected defer transaction to fail")
        } catch (_: Exception) {
            // Room/SQLite abort surfaces as exception from runInTransaction
        }

        assertEquals(0, database.deferEventDao().count())
        assertNull(database.questionOccurrenceDao().getById(occurrence.id)!!.deferredUntilEpochMillis)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, database.questionOccurrenceDao().getById(occurrence.id)!!.status)
    }

    @Test
    fun readApis_orderByOccurredAtThenId() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 10)
        timeProvider.setEpochMillis(epochAt(11, 11, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 30)

        val byOccurrence = database.deferEventDao().getForOccurrenceOrdered(occurrence.id)
        val byQuestion = database.deferEventDao().getForQuestionOrdered(occurrence.questionId)
        assertEquals(listOf(10, 30), byOccurrence.map { it.durationMinutes })
        assertEquals(byOccurrence.map { it.id }, byQuestion.map { it.id })
    }

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

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2024, 6, 1, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kyiv"
    }
}
