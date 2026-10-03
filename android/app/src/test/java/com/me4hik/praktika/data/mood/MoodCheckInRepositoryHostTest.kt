package com.me4hik.praktika.data.mood

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import kotlinx.coroutines.flow.first
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
class MoodCheckInRepositoryHostTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomMoodCheckInRepository
    private val timeProvider = FakeTimeProvider(
        epochMillis = 1_700_000_000_000L,
        zoneId = "Europe/Moscow",
    )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assetJson = checkNotNull(javaClass.classLoader)
            .getResourceAsStream("questions.json")?.bufferedReader()?.use { it.readText() }
            ?: error("questions.json missing from test resources")
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val seedResult = DatabaseSeeder().seedFromJson(
            json = assetJson,
            database = database,
            activeZoneId = "Europe/Moscow",
        )
        check(seedResult == SeedResult.Inserted || seedResult == SeedResult.AlreadyInitialized)
        repository = RoomMoodCheckInRepository(database, timeProvider)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun upsertInsertsThenReplacesSameOccurrence() = runBlocking {
        val occurrenceId = insertAvailableOccurrence()
        assertNull(repository.observeLevel(occurrenceId).first())

        repository.upsertForOccurrence(occurrenceId, MoodLevel.LOW)
        assertEquals(MoodLevel.LOW, repository.observeLevel(occurrenceId).first())
        val created = database.moodCheckInDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(1_700_000_000_000L, created.createdAtEpochMillis)

        timeProvider.advanceMillis(5_000L)
        repository.upsertForOccurrence(occurrenceId, MoodLevel.GREAT)
        val updated = database.moodCheckInDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(MoodLevel.GREAT, updated.level)
        assertEquals(created.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(1_700_000_005_000L, updated.updatedAtEpochMillis)
        assertEquals(1, database.moodCheckInDao().count())
    }

    private suspend fun insertAvailableOccurrence(): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Q1",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1_700_000_000_000L,
                availableUntilEpochMillis = 1_700_000_360_000L,
                openedAtEpochMillis = 1_700_000_010_000L,
                status = QuestionOccurrenceStatus.AVAILABLE,
                zoneId = "Europe/Moscow",
            ),
        )
    }
}
