// 04.08.2026 Cycle Engine cursor by Me4Hik START - instrumented-тесты CycleRepository
package com.me4hik.praktika.data.cycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositoryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
        repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun startPracticeBeforeElevenCreatesScheduledFirstOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertEquals(epochAt(11, 0, 0), occurrence.plannedAtEpochMillis)
    }

    @Test
    fun startPracticeAtElevenStartCreatesAvailableFirstOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
    }

    @Test
    fun startPracticeAtElevenFiftyNineCreatesAvailableFirstOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 59, 999_000_000))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
    }

    @Test
    fun startPracticeAtElevenOhOneCreatesScheduledAtFifteen() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 1, 0))
        repository.startPractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertEquals(epochAt(15, 0, 0), occurrence.plannedAtEpochMillis)
    }

    @Test
    fun startPracticeUsesQuestionTextSnapshot() = runBlocking {
        repository.startPractice()
        val question = database.questionDao().getByCyclePosition(1)!!
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(question.text, occurrence.questionTextSnapshot)
    }

    @Test
    fun startPracticeLeavesOpenedAtNull() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        assertNull(database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.openedAtEpochMillis)
    }

    @Test
    fun startPracticeUpdatesStateCursorToOneTwo() = runBlocking {
        repository.startPractice()
        val state = database.practiceStateDao().get()!!
        assertEquals(1, state.currentCycleNumber)
        assertEquals(2, state.nextCyclePosition)
        assertTrue(state.isPracticeStarted)
    }

    @Test
    fun repeatStartPracticeIsForbidden() = runBlocking {
        repository.startPractice()
        try {
            repository.startPractice()
            fail("Expected CycleAlreadyStartedException")
        } catch (_: CycleAlreadyStartedException) {
        }
    }

    @Test
    fun reconcilePromotesScheduledToAvailable() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.reconcile()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
    }

    @Test
    fun reconcileMarksAvailableAsMissedByTime() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(15, 0, 0))
        repository.reconcile()

        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, first.status)
        assertEquals(epochAt(15, 0, 0), first.completedAtEpochMillis)
    }

    @Test
    fun normalMissCreatesNextOccurrenceAtBoundarySlot() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(15, 0, 0))
        repository.reconcile()

        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(epochAt(15, 0, 0), second.plannedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, second.status)
    }

    @Test
    fun reconcileAfterTwoDaysCreatesFullHistory() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 6, 16, 0, 0))
        repository.reconcile()

        val occurrences = database.questionOccurrenceDao().getAllOrderedByPlannedAt()
        assertTrue(occurrences.size >= 4)
        assertEquals(1, occurrences.count { it.status == QuestionOccurrenceStatus.AVAILABLE })
        assertTrue(occurrences.count { it.status == QuestionOccurrenceStatus.MISSED_BY_TIME } >= 2)
    }

    @Test
    fun reconcileAfterMoreThanTwentyOneSlotsStartsCycleTwo() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 15, 12, 0, 0))
        repository.reconcile()

        assertTrue(database.questionOccurrenceDao().getByCycleAndPosition(2, 1) != null)
    }

    @Test
    fun repeatedReconcileDoesNotCreateDuplicates() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(20, 0, 0))
        repository.reconcile()
        repository.reconcile()

        assertEquals(2, database.questionOccurrenceDao().count())
    }

    @Test
    fun parallelReconcileDoesNotCreateDuplicates() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(20, 0, 0))

        awaitAll(
            async { repository.reconcile() },
            async { repository.reconcile() },
            async { repository.reconcile() },
        )

        assertEquals(2, database.questionOccurrenceDao().count())
    }

    @Test
    fun clockBackwardDoesNotCreateDuplicateOccurrences() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(16, 0, 0))
        repository.reconcile()
        val countBefore = database.questionOccurrenceDao().count()

        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.reconcile()

        assertEquals(countBefore, database.questionOccurrenceDao().count())
    }

    @Test
    fun clockForwardCreatesPastHistory() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 5, 20, 0, 0))
        repository.reconcile()

        assertTrue(database.questionOccurrenceDao().count() >= 3)
    }

    @Test
    fun skipAvailableByUserCreatesStrictlyNextOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.skipAvailableByUser()

        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, first.status)
        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(epochAt(15, 0, 0), second.plannedAtEpochMillis)
    }

    @Test
    fun deferAvailableKeepsSameOccurrenceAndClearsOpenedAt() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val before = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        database.questionOccurrenceDao().markOpenedIfNull(before.id, epochAt(11, 5, 0))
        timeProvider.setEpochMillis(epochAt(11, 10, 0))

        val result = repository.deferAvailableOccurrence(before.id, durationMinutes = 15)
        assertTrue(result is CycleResult.DeferCompleted)
        val deferred = result as CycleResult.DeferCompleted

        val after = database.questionOccurrenceDao().getById(before.id)!!
        assertEquals(before.id, after.id)
        assertEquals(before.cycleNumber, after.cycleNumber)
        assertEquals(before.cyclePosition, after.cyclePosition)
        assertEquals(before.plannedAtEpochMillis, after.plannedAtEpochMillis)
        assertEquals(before.availableUntilEpochMillis, after.availableUntilEpochMillis)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.status)
        assertNull(after.openedAtEpochMillis)
        assertEquals(epochAt(11, 25, 0), after.deferredUntilEpochMillis)
        assertEquals(epochAt(11, 25, 0), deferred.deferredUntilEpochMillis)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun deferRepeatedReplacesDeferredUntil() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 15)
        timeProvider.setEpochMillis(epochAt(11, 12, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)

        val after = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(epochAt(11, 17, 0), after.deferredUntilEpochMillis)
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun answerDuringDeferFinalizesSameOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 30)
        timeProvider.setEpochMillis(epochAt(11, 15, 0))
        repository.saveAnswer(occurrence.id, "answer during defer")

        val answered = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, answered.status)
        assertEquals(2, database.questionOccurrenceDao().count())
    }

    @Test
    fun reconcileConsumesMaturedDeferAndResetsOpenedAt() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 15)
        database.questionOccurrenceDao().markOpenedIfNull(occurrence.id, epochAt(11, 12, 0))
        assertEquals(epochAt(11, 25, 0), database.questionOccurrenceDao().getById(occurrence.id)!!.deferredUntilEpochMillis)
        assertEquals(epochAt(11, 12, 0), database.questionOccurrenceDao().getById(occurrence.id)!!.openedAtEpochMillis)

        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.reconcile()

        val after = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.status)
        assertNull(after.deferredUntilEpochMillis)
        assertNull(after.openedAtEpochMillis)
        assertEquals(occurrence.id, after.id)
    }

    @Test
    fun reconcileMaturedDeferSecondPassIsNoOp() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 5)
        database.questionOccurrenceDao().markOpenedIfNull(occurrence.id, epochAt(11, 11, 0))
        timeProvider.setEpochMillis(epochAt(11, 20, 0))
        repository.reconcile()
        repository.reconcile()

        val after = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertNull(after.deferredUntilEpochMillis)
        assertNull(after.openedAtEpochMillis)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, after.status)
    }

    @Test
    fun semanticExpiryBeatsMaturedDeferWithoutConsumingIntoQuestion() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 10, 0))
        repository.deferAvailableOccurrence(occurrence.id, durationMinutes = 30)
        database.questionOccurrenceDao().markOpenedIfNull(occurrence.id, epochAt(11, 12, 0))

        timeProvider.setEpochMillis(epochAt(15, 30, 0))
        repository.reconcile()

        val missed = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, missed.status)
        val current = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertTrue(current.id != occurrence.id)
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, current.status)
    }

    @Test
    fun pauseScheduledDoesNotCreateMissDuringPause() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(10, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        assertEquals(CycleResult.ReconcilePaused, repository.reconcile())

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
    }

    @Test
    fun resumeScheduledReschedulesSameRow() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        val original = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val originalId = original.id

        timeProvider.setEpochMillis(epochAt(10, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.resumePractice()

        val rescheduled = database.questionOccurrenceDao().getById(originalId)!!
        assertEquals(originalId, rescheduled.id)
        assertEquals(original.questionId, rescheduled.questionId)
        assertEquals(original.questionTextSnapshot, rescheduled.questionTextSnapshot)
        assertEquals(original.cycleNumber, rescheduled.cycleNumber)
        assertEquals(original.cyclePosition, rescheduled.cyclePosition)
        assertEquals(epochAt(15, 0, 0), rescheduled.plannedAtEpochMillis)
    }

    @Test
    fun pauseAvailableKeepsStatusAndResumeExtendsDeadline() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val originalUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis

        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 5, 10, 0, 0))
        repository.resumePractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
        assertEquals(originalUntil + (epochAtOnDate(2026, 8, 5, 10, 0, 0) - epochAt(12, 0, 0)), occurrence.availableUntilEpochMillis)
    }

    @Test
    fun pauseDoesNotCreateNewOccurrences() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        repository.pausePractice()
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun frozenMissUsesStrictlyNextSlotAfterShiftedDeadline() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val originalUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis

        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 5, 10, 0, 0))
        repository.resumePractice()

        val shiftedUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis
        assertTrue(shiftedUntil > originalUntil)

        timeProvider.setEpochMillis(shiftedUntil)
        repository.reconcile()

        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertTrue(second.plannedAtEpochMillis > shiftedUntil)
    }

    @Test
    fun negativePauseDurationDoesNotShrinkDeadline() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val originalUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis

        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.resumePractice()

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(originalUntil, occurrence.availableUntilEpochMillis)
    }

    @Test
    fun multipleAvailableOccurrencesThrowCorruption() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        database.questionOccurrenceDao().insert(
            first.copy(
                id = 0,
                cycleNumber = 1,
                cyclePosition = 2,
                status = QuestionOccurrenceStatus.AVAILABLE,
            ),
        )

        try {
            repository.reconcile()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    @Test
    fun multipleIncompleteOccurrencesThrowCorruption() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        database.questionOccurrenceDao().insert(
            first.copy(
                id = 0,
                cycleNumber = 1,
                cyclePosition = 2,
                status = QuestionOccurrenceStatus.SCHEDULED,
                plannedAtEpochMillis = epochAt(15, 0, 0),
                availableUntilEpochMillis = epochAt(19, 0, 0),
            ),
        )

        try {
            repository.reconcile()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    @Test
    fun closeAndReopenDatabasePreservesCycleState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("cycle_repo_test.db")
        if (file.exists()) {
            file.delete()
        }

        val persistentDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            "cycle_repo_test.db",
        ).build()

        runBlocking {
            seedBaseData(persistentDatabase)
            val persistentRepository = CycleRepository(
                persistentDatabase,
                FakeTimeProvider(epochAt(14, 0, 0), ZONE_KIEV),
                com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink,
            )
            persistentRepository.startPractice()
            persistentRepository.reconcile()
        }
        persistentDatabase.close()

        val reopenedDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            "cycle_repo_test.db",
        ).build()
        val occurrences = runBlocking { reopenedDatabase.questionOccurrenceDao().count() }
        reopenedDatabase.close()
        file.delete()

        assertTrue(occurrences >= 1)
    }

    @Test
    fun cycleDoesNotCreateAnswers() = runBlocking {
        timeProvider.setEpochMillis(epochAt(14, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(20, 0, 0))
        repository.reconcile()
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun reconcileBeforeStartReturnsNotStarted() = runBlocking {
        assertEquals(CycleResult.ReconcileNotStarted, repository.reconcile())
    }

    @Test
    fun reconcileScheduledWaitingDoesNotRewritePracticeState() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()

        val before = database.practiceStateDao().get()!!
        timeProvider.setEpochMillis(epochAt(9, 0, 0))
        assertEquals(CycleResult.ReconcileNoChanges, repository.reconcile())
        assertEquals(before, database.practiceStateDao().get()!!)
    }

    @Test
    fun reconcileAvailableWaitingDoesNotRewritePracticeState() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()

        val before = database.practiceStateDao().get()!!
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        assertEquals(CycleResult.ReconcileNoChanges, repository.reconcile())
        assertEquals(before, database.practiceStateDao().get()!!)
    }

    @Test
    fun frozenAvailableSkipSchedulesNextStrictlyAfterSkipTime() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 5, 10, 0, 0))
        repository.resumePractice()
        val skipAt = epochAtOnDate(2026, 8, 5, 11, 0, 0)
        timeProvider.setEpochMillis(skipAt)
        repository.skipAvailableByUser()

        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, first.status)
        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertTrue(second.plannedAtEpochMillis > skipAt)
        assertEquals(2, database.questionOccurrenceDao().count())
    }

    @Test
    fun pauseExactlyAtPlannedAtKeepsAvailableAndResumeExtendsDeadline() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val originalUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis

        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.pausePractice()
        assertEquals(
            QuestionOccurrenceStatus.AVAILABLE,
            database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
        )

        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.resumePractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, occurrence.status)
        assertEquals(originalUntil + (epochAt(12, 0, 0) - epochAt(11, 0, 0)), occurrence.availableUntilEpochMillis)
    }

    @Test
    fun pauseOneMillisecondBeforeDeadlineLeavesOneMillisecondRemainder() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val originalUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis
        val pauseAt = originalUntil - 1

        timeProvider.setEpochMillis(pauseAt)
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 5, 10, 0, 0))
        repository.resumePractice()

        val shiftedUntil = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.availableUntilEpochMillis
        assertEquals(1L, shiftedUntil - epochAtOnDate(2026, 8, 5, 10, 0, 0))

        timeProvider.setEpochMillis(shiftedUntil)
        repository.reconcile()

        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(QuestionOccurrenceStatus.MISSED_BY_TIME, first.status)
        assertEquals(shiftedUntil, first.completedAtEpochMillis)
    }

    @Test
    fun resumeAfterMultipleCalendarSlotsPreservesOccurrenceId() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        val originalId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id

        timeProvider.setEpochMillis(epochAt(10, 0, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAtOnDate(2026, 8, 6, 10, 0, 0))
        repository.resumePractice()

        assertEquals(1, database.questionOccurrenceDao().count())
        val rescheduled = database.questionOccurrenceDao().getById(originalId)!!
        assertEquals(originalId, rescheduled.id)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, rescheduled.status)
        assertTrue(rescheduled.plannedAtEpochMillis > epochAtOnDate(2026, 8, 6, 10, 0, 0))

        timeProvider.setEpochMillis(rescheduled.plannedAtEpochMillis)
        repository.reconcile()
        val afterOpen = database.questionOccurrenceDao().getById(originalId)!!
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, afterOpen.status)
    }

    @Test
    fun skipAvailableByUserWithMatchingIdSetsCompletedAt() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.skipAvailableByUser(occurrence.id)

        val first = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, first.status)
        assertEquals(epochAt(11, 30, 0), first.completedAtEpochMillis)
        assertEquals(0, database.answerDao().count())
        assertEquals(2, database.questionOccurrenceDao().count())
    }

    @Test
    fun skipAvailableByUserWithWrongIdDoesNotMutateCurrentOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.skipAvailableByUser(first.id)

        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        try {
            repository.skipAvailableByUser(first.id)
            fail("Expected CycleSkipNotAllowedException")
        } catch (_: CycleSkipNotAllowedException) {
            // expected
        }

        val refreshedSecond = database.questionOccurrenceDao().getById(second.id)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, refreshedSecond.status)
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun skipAvailableByUserRaceAfterCurrentChangedDoesNotSkipNewOccurrence() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val first = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val staleId = first.id
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.skipAvailableByUser(staleId)

        val second = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        try {
            repository.skipAvailableByUser(staleId)
            fail("Expected CycleSkipNotAllowedException")
        } catch (_: CycleSkipNotAllowedException) {
            // expected
        }

        assertEquals(QuestionOccurrenceStatus.SCHEDULED, database.questionOccurrenceDao().getById(second.id)!!.status)
    }

    @Test
    fun concurrentSkipSameIdAllowsOnlyOneSuccess() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrenceId = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        timeProvider.setEpochMillis(epochAt(11, 30, 0))

        val results = listOf(
            async { runCatching { repository.skipAvailableByUser(occurrenceId) } },
            async { runCatching { repository.skipAvailableByUser(occurrenceId) } },
        ).awaitAll()

        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure }
        assertEquals(1, successes)
        assertEquals(1, failures)
        assertEquals(1, database.questionOccurrenceDao().countByStatus(QuestionOccurrenceStatus.SKIPPED_BY_USER))
        assertEquals(0, database.answerDao().count())
    }

    private suspend fun seedBaseData(target: PraktikaDatabase = database) {
        val questions = (1..21).map { index ->
            QuestionEntity(
                id = index,
                cyclePosition = index,
                text = "Question $index",
                isActive = true,
            )
        }
        target.questionDao().insertAll(questions)
        target.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
                ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
            ),
        )
        target.practiceStateDao().insert(
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

    private fun epochAt(hour: Int, minute: Int, second: Int, nano: Int = 0): Long {
        return epochAtOnDate(2026, 8, 4, hour, minute, second, nano)
    }

    private fun epochAtOnDate(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        nano: Int = 0,
    ): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, nano, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE_KIEV = "Europe/Kiev"
    }
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
