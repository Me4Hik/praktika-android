package com.me4hik.praktika.measurement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.cycle.CycleAlreadyStartedException
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.mood.RoomMoodCheckInRepository
import com.me4hik.praktika.data.model.MoodLevel
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CycleRepositoryMeasurementHostTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var tracker: FakeAnalyticsTracker
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV)
        tracker = FakeAnalyticsTracker()
        repository = CycleRepository(
            database,
            timeProvider,
            NoOpBackupMutationRequestSink,
            context,
            analyticsTracker = tracker,
            analyticsFlavor = "test",
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun startPractice_emitsPracticeStartedOnce() = runBlocking {
        assertTrue(repository.startPractice() is CycleResult.PracticeStarted)
        assertEquals(1, tracker.count(AnalyticsEventNames.PRACTICE_STARTED))

        try {
            repository.startPractice()
        } catch (_: CycleAlreadyStartedException) {
            // expected
        }
        assertEquals(1, tracker.count(AnalyticsEventNames.PRACTICE_STARTED))
    }

    @Test
    fun markOpened_emitsOnlyOnFirstOpen() = runBlocking {
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!

        assertTrue(repository.markOccurrenceOpened(occurrence.id))
        assertEquals(1, tracker.count(AnalyticsEventNames.QUESTION_OPENED))

        assertTrue(!repository.markOccurrenceOpened(occurrence.id))
        assertEquals(1, tracker.count(AnalyticsEventNames.QUESTION_OPENED))
    }

    @Test
    fun answerSaved_emitsOnSuccessOnly_withoutAnswerText() = runBlocking {
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        tracker.clear()

        try {
            repository.saveAnswer(occurrence.id, "   ")
        } catch (_: Exception) {
            // expected blank rejection
        }
        assertEquals(0, tracker.count(AnalyticsEventNames.ANSWER_SAVED))

        assertTrue(repository.saveAnswer(occurrence.id, "ok") is CycleResult.AnswerSaved)
        assertEquals(1, tracker.count(AnalyticsEventNames.ANSWER_SAVED))
        assertTrue(
            tracker.recorded().single { it.name == AnalyticsEventNames.ANSWER_SAVED }
                .params.asMap()
                .containsKey("question_id"),
        )
        assertTrue(
            tracker.recorded().none { event ->
                event.params.asMap().values.any { it.contains("ok") }
            },
        )
    }

    @Test
    fun skipAndDefer_emitOnceEach() = runBlocking {
        repository.startPractice()
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        tracker.clear()

        assertTrue(repository.skipAvailableByUser(first.id) is CycleResult.SkipCompleted)
        assertEquals(1, tracker.count(AnalyticsEventNames.QUESTION_SKIPPED))

        // advance into next available window for question 2
        timeProvider.setEpochMillis(epochAt(15, 0, 0))
        repository.reconcile()
        val second = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertTrue(
            repository.deferAvailableOccurrence(second.id, durationMinutes = 10)
                is CycleResult.DeferCompleted,
        )
        assertEquals(1, tracker.count(AnalyticsEventNames.QUESTION_DEFERRED))
        val deferred = tracker.recorded().single { it.name == AnalyticsEventNames.QUESTION_DEFERRED }
        assertEquals("10", deferred.params.asMap()["duration_minutes"])
    }

    @Test
    fun moodCheckin_emitsFactOnlyWithoutLevel() = runBlocking {
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        repository.saveAnswer(occurrence.id, "answered")
        tracker.clear()

        val moodRepo = RoomMoodCheckInRepository(
            database = database,
            timeProvider = timeProvider,
            analyticsTracker = tracker,
        )
        moodRepo.upsertForOccurrence(occurrence.id, MoodLevel.GREAT)
        assertEquals(1, tracker.count(AnalyticsEventNames.MOOD_CHECKIN_SAVED))
        assertTrue(tracker.recorded().single().params.isEmpty())
    }

    @Test
    fun throwingTracker_doesNotBreakStartPractice() = runBlocking {
        val throwing = object : AnalyticsTracker {
            override fun track(event: AnalyticsEvent) {
                throw IllegalStateException("boom")
            }
        }
        val repo = CycleRepository(
            database,
            timeProvider,
            NoOpBackupMutationRequestSink,
            context,
            analyticsTracker = throwing,
            analyticsFlavor = "test",
        )
        // reset practice state for a fresh start on same DB is hard; use already started path:
        // create new DB
        database.close()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        val fresh = CycleRepository(
            database,
            timeProvider,
            NoOpBackupMutationRequestSink,
            context,
            analyticsTracker = throwing,
            analyticsFlavor = "test",
        )
        assertTrue(fresh.startPractice() is CycleResult.PracticeStarted)
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
