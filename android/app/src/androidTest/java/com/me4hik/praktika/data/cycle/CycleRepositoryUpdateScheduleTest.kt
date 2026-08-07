// 06.08.2026 Settings Schedule cursor by Me4Hik START - instrumented tests updateSchedule
package com.me4hik.praktika.data.cycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositoryUpdateScheduleTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
        repository = CycleRepository(database, timeProvider)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun updateBeforeStartPersistsSlots() = runBlocking {
        repository.updateSchedule(defaultUpdates(630, 860, 1210))
        assertSlotMinutes(630, 860, 1210)
    }

    @Test
    fun updateBeforeStartReadableFromDatabase() = runBlocking {
        repository.updateSchedule(defaultUpdates(480, 720, 1020))
        assertSlotMinutes(480, 720, 1020)
        assertSlotMinutes(480, 720, 1020)
    }

    @Test
    fun requiresExactlyThreeSlots() = runBlocking {
        assertValidation(ScheduleValidationReason.INVALID_SLOT_COUNT) {
            repository.updateSchedule(
                listOf(ScheduleSlotUpdate(1, 600), ScheduleSlotUpdate(2, 900)),
            )
        }
    }

    @Test
    fun rejectsInvalidSlotIndex() = runBlocking {
        assertValidation(ScheduleValidationReason.INVALID_SLOT_INDEX) {
            repository.updateSchedule(
                listOf(
                    ScheduleSlotUpdate(0, 600),
                    ScheduleSlotUpdate(2, 900),
                    ScheduleSlotUpdate(3, 1140),
                ),
            )
        }
    }

    @Test
    fun rejectsOutOfRangeTime() = runBlocking {
        assertValidation(ScheduleValidationReason.TIME_OUT_OF_RANGE) {
            repository.updateSchedule(defaultUpdates(1440, 900, 1140))
        }
    }

    @Test
    fun rejectsDuplicateTime() = runBlocking {
        assertValidation(ScheduleValidationReason.DUPLICATE_TIME) {
            repository.updateSchedule(defaultUpdates(660, 660, 1140))
        }
    }

    @Test
    fun acceptsUnsortedInput() = runBlocking {
        repository.updateSchedule(
            listOf(
                ScheduleSlotUpdate(3, 1140),
                ScheduleSlotUpdate(1, 480),
                ScheduleSlotUpdate(2, 720),
            ),
        )
        assertSlotMinutes(480, 720, 1140)
    }

    @Test
    fun preservesSlotIndexesDuringSwap() = runBlocking {
        repository.updateSchedule(defaultUpdates(900, 660, 1140))
        val slots = database.scheduleSlotDao().getAllOrderedByTime()
        assertEquals(setOf(1, 2, 3), slots.map { it.slotIndex }.toSet())
    }

    @Test
    fun swapElevenAndFifteenWithoutUniqueViolation() = runBlocking {
        repository.updateSchedule(defaultUpdates(900, 660, 1140))
        assertSlotMinutes(900, 660, 1140)
    }

    @Test
    fun invalidInputDoesNotMutateDatabase() = runBlocking {
        val before = database.scheduleSlotDao().getAllOrderedByTime()
        try {
            repository.updateSchedule(defaultUpdates(660, 660, 1140))
            fail("Expected ScheduleValidationException")
        } catch (_: ScheduleValidationException) {
            assertEquals(before, database.scheduleSlotDao().getAllOrderedByTime())
        }
    }

    @Test
    fun scheduledReAnchorsToNearestNewSlot() = runBlocking {
        repository.startPractice()
        repository.updateSchedule(defaultUpdates(540, 840, 1080))
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(epochAt(9, 0, 0), occurrence.plannedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
    }

    @Test
    fun sameMinuteScheduledBecomesAvailable() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        repository.updateSchedule(defaultUpdates(660, 900, 1140))
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
    }

    @Test
    fun scheduledPreservesIdentityAndCursor() = runBlocking {
        repository.startPractice()
        val before = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val stateBefore = database.practiceStateDao().get()!!
        repository.updateSchedule(defaultUpdates(540, 840, 1080))
        val after = database.questionOccurrenceDao().getById(before.id)!!
        val stateAfter = database.practiceStateDao().get()!!
        assertEquals(before.id, after.id)
        assertEquals(before.questionId, after.questionId)
        assertEquals(before.questionTextSnapshot, after.questionTextSnapshot)
        assertEquals(before.cyclePosition, after.cyclePosition)
        assertEquals(stateBefore.nextCyclePosition, stateAfter.nextCyclePosition)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun availableKeepsHistoricalPlannedAtAndSlotIndex() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val before = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        val after = database.questionOccurrenceDao().getById(before.id)!!
        assertEquals(before.plannedAtEpochMillis, after.plannedAtEpochMillis)
        assertEquals(before.scheduleSlotIndex, after.scheduleSlotIndex)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.status)
    }

    @Test
    fun availableUntilShortens() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(epochAt(13, 0, 0), occurrence.availableUntilEpochMillis)
    }

    @Test
    fun availableUntilExtends() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.updateSchedule(defaultUpdates(540, 900, 1320))
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(epochAt(15, 0, 0), occurrence.availableUntilEpochMillis)
    }

    @Test
    fun currentMinuteSlotIsNotUsedAsAvailableUntil() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        repository.updateSchedule(defaultUpdates(660, 900, 1140))
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(epochAt(15, 0, 0), occurrence.availableUntilEpochMillis)
    }

    @Test
    fun expiredAvailableIsMissedBeforeScheduleUpdate() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(15, 0, 1))
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, first.status)
        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertTrue(
            incomplete.status == QuestionOccurrenceStatus.SCHEDULED ||
                incomplete.status == QuestionOccurrenceStatus.AVAILABLE,
        )
    }

    @Test
    fun scheduleUpdateDoesNotReviveMissedOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(15, 0, 0))
        repository.reconcile()
        val missedId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        val missed = database.questionOccurrenceDao().getById(missedId)!!
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, missed.status)
    }

    @Test
    fun pausedScheduledSaveDoesNotChangeOccurrence() = runBlocking {
        repository.startPractice()
        val before = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        repository.pausePractice()
        repository.updateSchedule(defaultUpdates(540, 840, 1080))
        val after = database.questionOccurrenceDao().getById(before.id)!!
        assertEquals(before, after)
        assertSlotMinutes(540, 840, 1080)
    }

    @Test
    fun pausedScheduledResumeUsesNewSlots() = runBlocking {
        repository.startPractice()
        val id = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        timeProvider.setEpochMillis(epochAt(8, 30, 0))
        repository.pausePractice()
        repository.updateSchedule(defaultUpdates(540, 840, 1080))
        timeProvider.setEpochMillis(epochAt(8, 45, 0))
        repository.resumePractice()
        val after = database.questionOccurrenceDao().getById(id)!!
        assertEquals(epochAt(9, 0, 0), after.plannedAtEpochMillis)
    }

    @Test
    fun pausedAvailableSaveDoesNotChangeOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val before = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        val after = database.questionOccurrenceDao().getById(before.id)!!
        assertEquals(before, after)
    }

    @Test
    fun pausedAvailableResumePreservesRemainingWindowWithoutDoubleExtension() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val originalUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.resumePractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(originalUntil + (epochAt(14, 0, 0) - epochAt(12, 0, 0)), occurrence.availableUntilEpochMillis)
    }

    @Test
    fun nextOccurrenceUsesLatestScheduleAfterPausedAvailableCompletes() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        repository.updateSchedule(defaultUpdates(540, 780, 1320))
        timeProvider.setEpochMillis(epochAt(12, 30, 0))
        repository.resumePractice()
        repository.skipAvailableByUser()
        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(epochAt(13, 0, 0), second.plannedAtEpochMillis)
    }

    @Test
    fun crossMidnightScheduleAccepted() = runBlocking {
        repository.updateSchedule(defaultUpdates(1380, 60, 720))
        assertSlotMinutes(1380, 60, 720)
    }

    @Test
    fun concurrentSaveAnswerAndUpdateScheduleAreSerialized() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
        val cursorBefore = database.practiceStateDao().get()!!.nextCyclePosition
        val startGate = CompletableDeferred<Unit>()
        val results = listOf(
            async {
                startGate.await()
                runCatching {
                    repository.saveAnswer(occurrence.id, "Concurrent answer")
                }
            },
            async {
                startGate.await()
                runCatching {
                    repository.updateSchedule(defaultUpdates(540, 840, 1080))
                }
            },
        )
        startGate.complete(Unit)
        assertTrue(results.awaitAll().all { it.isSuccess })
        assertEquals(1, database.answerDao().count())
        val answered = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, answered.status)
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)
        assertSlotMinutes(540, 840, 1080)
        assertEquals(cursorBefore + 1, database.practiceStateDao().get()!!.nextCyclePosition)
        assertQuickCheckOk()
    }

    @Test
    fun concurrentSkipAndUpdateScheduleAreSerialized() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
        val cursorBefore = database.practiceStateDao().get()!!.nextCyclePosition
        val startGate = CompletableDeferred<Unit>()
        val results = listOf(
            async {
                startGate.await()
                runCatching {
                    repository.skipAvailableByUser(occurrence.id)
                }
            },
            async {
                startGate.await()
                runCatching {
                    repository.updateSchedule(defaultUpdates(540, 840, 1080))
                }
            },
        )
        startGate.complete(Unit)
        assertTrue(results.awaitAll().all { it.isSuccess })
        assertEquals(0, database.answerDao().count())
        val first = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, first.status)
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)
        assertSlotMinutes(540, 840, 1080)
        assertEquals(cursorBefore + 1, database.practiceStateDao().get()!!.nextCyclePosition)
        assertQuickCheckOk()
    }

    @Test
    fun concurrentReconcileAndUpdateAreSerialized() = runBlocking {
        repository.startPractice()
        val results = listOf(
            async { runCatching { repository.reconcile() } },
            async { runCatching { repository.updateSchedule(defaultUpdates(540, 840, 1080)) } },
        ).awaitAll()
        assertTrue(results.all { it.isSuccess })
    }

    @Test
    fun seedDoesNotRestoreUserSchedule() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = CycleRepository(database, timeProvider)
        val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        DatabaseSeeder().seedFromJson(assetJson, database, ZONE_KIEV)
        repository.updateSchedule(defaultUpdates(480, 720, 1020))
        val result = DatabaseSeeder().seedFromJson(assetJson, database, ZONE_KIEV)
        assertEquals(SeedResult.AlreadyInitialized, result)
        assertSlotMinutes(480, 720, 1020)
    }

    @Test
    fun roomSchemaVersionRemainsTwo() {
        assertEquals(2, database.openHelper.readableDatabase.version)
    }

    private suspend fun assertValidation(
        expected: ScheduleValidationReason,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected ScheduleValidationException")
        } catch (exception: ScheduleValidationException) {
            assertEquals(expected, exception.reason)
        }
    }

    private suspend fun assertSlotMinutes(first: Int, second: Int, third: Int) {
        val byIndex = database.scheduleSlotDao().getAllOrderedByTime().associateBy { it.slotIndex }
        assertEquals(first, byIndex.getValue(1).timeOfDayMinutes)
        assertEquals(second, byIndex.getValue(2).timeOfDayMinutes)
        assertEquals(third, byIndex.getValue(3).timeOfDayMinutes)
    }

    private fun defaultUpdates(first: Int, second: Int, third: Int): List<ScheduleSlotUpdate> {
        return listOf(
            ScheduleSlotUpdate(1, first),
            ScheduleSlotUpdate(2, second),
            ScheduleSlotUpdate(3, third),
        )
    }

    private suspend fun seedBaseData() {
        val questions = (1..21).map { position ->
            QuestionEntity(
                id = position,
                cyclePosition = position,
                text = "Question $position",
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

    private fun assertQuickCheckOk() {
        database.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
    }

    private fun epochAt(hour: Int, minute: Int, second: Int, nano: Int = 0): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, nano, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
