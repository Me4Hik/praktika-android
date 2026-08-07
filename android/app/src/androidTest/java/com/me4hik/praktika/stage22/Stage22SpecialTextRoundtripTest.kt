// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - special text Room round-trip
package com.me4hik.praktika.stage22

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage22SpecialTextRoundtripTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomArchiveReadRepository

    private val questionSnapshot = "Historical «snapshot» question 🎨"

    private val specialAnswerText = buildString {
        appendLine("Кириллица: Тёплый «красный» 🎉")
        appendLine("hello, \"world\"")
        appendLine("line1\r\nline2\nline3")
        appendLine("# not header")
        appendLine("- not list")
        appendLine("```code```")
        appendLine("=1+1")
        appendLine("<tag> & 'apostrophe'")
        appendLine(" leading/trailing ")
        appendLine("`inline`")
        appendLine("👨‍👩‍👧")
    }.trimEnd()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomArchiveReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun specialTextRoundTripExactInRoomAndArchiveProjection() = runBlocking {
        seedBase()
        val occurrenceId = database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = questionSnapshot,
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1_000L,
                availableUntilEpochMillis = 2_000L,
                completedAtEpochMillis = 1_500L,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = "UTC",
            ),
        )
        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = specialAnswerText,
                createdAtEpochMillis = 1_500L,
            ),
        )

        val storedAnswer = database.answerDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(specialAnswerText, storedAnswer.text)

        val storedOccurrence = database.questionOccurrenceDao().getById(occurrenceId)!!
        assertEquals(questionSnapshot, storedOccurrence.questionTextSnapshot)

        val archiveEntry = repository.observeEntries().first().single()
        assertEquals(specialAnswerText, archiveEntry.answerText)
        assertEquals(questionSnapshot, archiveEntry.questionText)
    }

    private suspend fun seedBase() {
        database.questionDao().insertAll(
            listOf(QuestionEntity(id = 1, cyclePosition = 1, text = "Live question text")),
        )
        database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 660)))
        database.practiceStateDao().insert(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = 100L,
                currentCycleNumber = 1,
                nextCyclePosition = 2,
                lastProcessedAtEpochMillis = 100L,
                pausedAtEpochMillis = null,
                activeZoneId = "UTC",
                seedVersion = 1,
            ),
        )
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END
