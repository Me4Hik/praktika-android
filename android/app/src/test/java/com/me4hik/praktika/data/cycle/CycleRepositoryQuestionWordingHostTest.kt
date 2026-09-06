package com.me4hik.praktika.data.cycle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.QuestionDisplayTextResolver
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.data.preferences.QuestionWordingModeSource
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationOccurrenceSnapshot
import com.me4hik.praktika.notification.NotificationPlanner
import com.me4hik.praktika.notification.NotificationPlanningInput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CycleRepositoryQuestionWordingHostTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private val wordingMode = AtomicReference(QuestionWordingMode.MASCULINE)
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(11, 0, 0), ZONE_KIEV)
        repository = CycleRepository(
            database,
            timeProvider,
            NoOpBackupMutationRequestSink,
            wordingModeSource = QuestionWordingModeSource { wordingMode.get() },
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun createSnapshots_masculine_usesCanonicalText() = runBlocking {
        assertCreatedQuestionTwoSnapshot(QuestionWordingMode.MASCULINE, canonical(2))
    }

    @Test
    fun createSnapshots_feminine_usesFeminineText() = runBlocking {
        assertCreatedQuestionTwoSnapshot(QuestionWordingMode.FEMININE, feminine(2))
    }

    @Test
    fun createSnapshots_neutral_usesNeutralText() = runBlocking {
        assertCreatedQuestionTwoSnapshot(QuestionWordingMode.NEUTRAL, neutral(2))
    }

    @Test
    fun applyWordingMode_updatesOnlyIncompleteDependentOccurrence() = runBlocking {
        wordingMode.set(QuestionWordingMode.MASCULINE)
        insertIncomplete(questionId = 2, snapshot = canonical(2))
        insertTerminalAnswered(questionId = 2, snapshot = canonical(2), occurrenceId = 100L)

        val changed = repository.applyQuestionWordingMode(QuestionWordingMode.FEMININE)
        assertTrue(changed)
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(feminine(2), incomplete.questionTextSnapshot)
        val terminal = database.questionOccurrenceDao().getById(100L)!!
        assertEquals(canonical(2), terminal.questionTextSnapshot)
    }

    @Test
    fun applyWordingMode_ignoresNonDependentIncomplete() = runBlocking {
        insertIncomplete(questionId = 1, snapshot = "Question 1")
        val changed = repository.applyQuestionWordingMode(QuestionWordingMode.FEMININE)
        assertFalse(changed)
        assertEquals(
            "Question 1",
            database.questionOccurrenceDao().getIncompleteOrdered().single().questionTextSnapshot,
        )
    }

    @Test
    fun notificationPlanner_usesUpdatedIncompleteSnapshot() = runBlocking {
        insertIncomplete(questionId = 5, snapshot = canonical(5), status = QuestionOccurrenceStatus.AVAILABLE)
        assertTrue(repository.applyQuestionWordingMode(QuestionWordingMode.NEUTRAL))
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(neutral(5), incomplete.questionTextSnapshot)

        val plan = NotificationPlanner.plan(
            input = NotificationPlanningInput(
                isPracticeStarted = true,
                isPaused = false,
                nowEpochMillis = incomplete.plannedAtEpochMillis + 1_000L,
                currentOccurrence = NotificationOccurrenceSnapshot(
                    occurrenceId = incomplete.id,
                    status = incomplete.status,
                    plannedAtEpochMillis = incomplete.plannedAtEpochMillis,
                    availableUntilEpochMillis = incomplete.availableUntilEpochMillis,
                    questionTextSnapshot = incomplete.questionTextSnapshot,
                    openedAtEpochMillis = incomplete.openedAtEpochMillis,
                    deferredUntilEpochMillis = incomplete.deferredUntilEpochMillis,
                    zoneId = incomplete.zoneId,
                ),
                notificationCapability = NotificationDeliveryCapability.ENABLED,
                activeNotificationOccurrenceId = null,
            ),
            soundEnabled = true,
        )
        assertEquals(neutral(5), plan.showNotification?.questionTextSnapshot)
    }

    private suspend fun assertCreatedQuestionTwoSnapshot(
        mode: QuestionWordingMode,
        expectedText: String,
    ) {
        wordingMode.set(mode)
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 0, 30))
        val saved = repository.saveAnswer(first.id, "answer")
        assertTrue(saved is CycleResult.AnswerSaved)
        val created = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(2, created.questionId)
        assertEquals(expectedText, created.questionTextSnapshot)
    }

    private suspend fun insertIncomplete(
        questionId: Int,
        snapshot: String,
        status: QuestionOccurrenceStatus = QuestionOccurrenceStatus.AVAILABLE,
    ) {
        database.practiceStateDao().update(
            database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                currentCycleNumber = 1,
                nextCyclePosition = questionId % 21 + 1,
                practiceStartedAtEpochMillis = epochAt(11, 0, 0),
                lastProcessedAtEpochMillis = epochAt(11, 0, 0),
            ),
        )
        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = snapshot,
                cycleNumber = 1,
                cyclePosition = questionId,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(11, 0, 0),
                availableUntilEpochMillis = epochAt(15, 0, 0),
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = status,
                zoneId = ZONE_KIEV,
            ),
        )
    }

    private suspend fun insertTerminalAnswered(
        questionId: Int,
        snapshot: String,
        occurrenceId: Long,
    ) {
        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                id = occurrenceId,
                questionId = questionId,
                questionTextSnapshot = snapshot,
                cycleNumber = 2,
                cyclePosition = questionId,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(8, 0, 0),
                availableUntilEpochMillis = epochAt(8, 1, 0),
                openedAtEpochMillis = epochAt(8, 0, 0),
                completedAtEpochMillis = epochAt(8, 0, 30),
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = ZONE_KIEV,
            ),
        )
    }

    private suspend fun seedBaseData() {
        val questions = (1..21).map { index ->
            QuestionEntity(
                id = index,
                cyclePosition = index,
                text = when (index) {
                    2 -> canonical(2)
                    5 -> canonical(5)
                    8 -> canonical(8)
                    11 -> canonical(11)
                    else -> "Question $index"
                },
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

    private fun canonical(id: Int): String = when (id) {
        2 -> "Какой настоящий я сейчас по цвету?"
        5 -> "Какой настоящий я сейчас по запаху?"
        8 -> "Какой настоящий я сейчас по звуку?"
        11 -> "Какой настоящий я сейчас на ощупь?"
        else -> error(id)
    }

    private fun feminine(id: Int): String =
        QuestionDisplayTextResolver.resolve(id, canonical(id), QuestionWordingMode.FEMININE)

    private fun neutral(id: Int): String =
        QuestionDisplayTextResolver.resolve(id, canonical(id), QuestionWordingMode.NEUTRAL)

    private companion object {
        const val ZONE_KIEV = "Europe/Kyiv"
    }
}
