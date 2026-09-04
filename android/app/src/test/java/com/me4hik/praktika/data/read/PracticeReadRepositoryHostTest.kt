package com.me4hik.praktika.data.read

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class PracticeReadRepositoryHostTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomPracticeReadRepository

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = RoomPracticeReadRepository(database)
            database.questionDao().insertAll(
                listOf(
                    QuestionEntity(id = 1, cyclePosition = 1, text = "Q1", isActive = true),
                ),
            )
            database.scheduleSlotDao().insertAll(
                listOf(
                    com.me4hik.praktika.data.local.entity.ScheduleSlotEntity(
                        slotIndex = 1,
                        timeOfDayMinutes = 660,
                    ),
                ),
            )
            database.practiceStateDao().insert(
                PracticeStateEntity(
                    id = 1,
                    isPracticeStarted = true,
                    isPaused = false,
                    practiceStartedAtEpochMillis = 1L,
                    currentCycleNumber = 1,
                    nextCyclePosition = 2,
                    lastProcessedAtEpochMillis = 1L,
                    pausedAtEpochMillis = null,
                    activeZoneId = "UTC",
                    seedVersion = 1,
                ),
            )
            database.questionOccurrenceDao().insert(
                QuestionOccurrenceEntity(
                    id = 10L,
                    questionId = 1,
                    cycleNumber = 1,
                    cyclePosition = 1,
                    scheduleSlotIndex = 1,
                    plannedAtEpochMillis = 1_000L,
                    availableUntilEpochMillis = 2_000L,
                    zoneId = "UTC",
                    questionTextSnapshot = "Q1",
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    openedAtEpochMillis = 1_400L,
                    deferredUntilEpochMillis = 1_800L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun readSnapshot_seesConsumeImmediately() = runBlocking {
        val before = repository.readSnapshot()
        assertEquals(1_800L, before.incompleteOccurrence!!.deferredUntilEpochMillis)
        assertEquals(1_400L, before.incompleteOccurrence!!.openedAtEpochMillis)

        database.questionOccurrenceDao().consumeMaturedDeferIfDue(
            id = 10L,
            nowEpochMillis = 1_900L,
        )

        val after = repository.readSnapshot()
        assertNull(after.incompleteOccurrence!!.deferredUntilEpochMillis)
        assertNull(after.incompleteOccurrence!!.openedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.incompleteOccurrence!!.status)
    }
}
