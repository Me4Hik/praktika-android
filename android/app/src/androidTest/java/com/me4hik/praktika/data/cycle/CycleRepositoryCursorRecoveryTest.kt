// 10.08.2026 Post-release fixes cursor by Me4Hik START - cursor recovery matrix
package com.me4hik.praktika.data.cycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositoryCursorRecoveryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        timeProvider = FakeTimeProvider(
            Vc6CursorDesyncFixtureSupport.reconcileNowEpochMillis(),
            Vc6CursorDesyncFixtureSupport.ZONE_KIEV,
        )
        repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun originalCrashFixture_recoversWithoutCrash() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installProductionVc6DesyncFingerprint(database)

        val occ6Before = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        val occ6Id = occ6Before.id
        val answerCountBefore = database.answerDao().count()

        repository.syncEnvironmentAndReconcile()

        val occ6After = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        assertEquals(occ6Id, occ6After.id)
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occ6After.status)
        assertEquals(answerCountBefore, database.answerDao().count())
        assertTrue(database.practiceStateDao().get()!!.nextCyclePosition >= 7)
    }

    @Test
    fun recoveryIsIdempotentAcrossThreeReconciles() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installProductionVc6DesyncFingerprint(database)

        repository.syncEnvironmentAndReconcile()
        val afterFirstState = database.practiceStateDao().get()!!
        val occurrenceCount = database.questionOccurrenceDao().count()
        val answers = database.answerDao().count()
        val occ6Id = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!.id

        repository.syncEnvironmentAndReconcile()
        repository.syncEnvironmentAndReconcile()

        val afterThirdState = database.practiceStateDao().get()!!
        assertEquals(afterFirstState.nextCyclePosition, afterThirdState.nextCyclePosition)
        assertEquals(occurrenceCount, database.questionOccurrenceDao().count())
        assertEquals(answers, database.answerDao().count())
        assertEquals(occ6Id, database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!.id)
    }

    @Test
    fun matrixA_answeredAtCursor_advancesToSeven() = runBlocking {
        runMatrixRecovery(
            existingAtCursorStatus = QuestionOccurrenceStatus.ANSWERED,
            withAnswerAtCursor = true,
            expectedNext = 7,
        )
    }

    @Test
    fun matrixB_missedAtCursor_advancesToSeven() = runBlocking {
        runMatrixRecovery(
            existingAtCursorStatus = QuestionOccurrenceStatus.MISSED_BY_TIME,
            expectedNext = 7,
        )
    }

    @Test
    fun matrixC_skippedAtCursor_advancesToSeven() = runBlocking {
        runMatrixRecovery(
            existingAtCursorStatus = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            expectedNext = 7,
        )
    }

    @Test
    fun matrixD_scheduledAtCursor_preservesRowId() = runBlocking {
        runMatrixRecovery(
            existingAtCursorStatus = QuestionOccurrenceStatus.SCHEDULED,
            includeStaleIncompleteAt5 = false,
            expectedNext = 7,
        )
    }

    @Test
    fun matrixE_availableAtCursor_preservesRowId() = runBlocking {
        runMatrixRecovery(
            existingAtCursorStatus = QuestionOccurrenceStatus.AVAILABLE,
            includeStaleIncompleteAt5 = false,
            expectedNext = 7,
        )
    }

    @Test
    fun matrixF_terminalRowsSixSevenEight_advancesToNine() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installDesyncScenario(
            database = database,
            existingAtCursorStatus = QuestionOccurrenceStatus.MISSED_BY_TIME,
            includeStaleIncompleteAt5 = false,
            extraTerminalAfterCursor = 2,
        )

        repository.syncEnvironmentAndReconcile()
        assertEquals(9, database.practiceStateDao().get()!!.nextCyclePosition)
        assertNotNull(database.questionOccurrenceDao().getByCycleAndPosition(1, 8))
    }

    @Test
    fun matrixG_gapAtSeven_repairStopsAtSeven() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installDesyncScenario(
            database = database,
            existingAtCursorStatus = QuestionOccurrenceStatus.ANSWERED,
            includeStaleIncompleteAt5 = false,
            withAnswerAtCursor = true,
            includeGapAt7 = true,
        )

        repository.syncEnvironmentAndReconcile()
        assertEquals(7, database.practiceStateDao().get()!!.nextCyclePosition)
        assertNotNull(database.questionOccurrenceDao().getByCycleAndPosition(1, 8))
    }

    @Test
    fun matrixH_twoIncomplete_isUnsafeAndDoesNotMutate() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installTwoIncompleteConflict(database)

        val stateBefore = database.practiceStateDao().get()!!
        val countBefore = database.questionOccurrenceDao().count()

        val result = runCatching { repository.syncEnvironmentAndReconcile() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CycleCorruptionException)

        assertEquals(stateBefore, database.practiceStateDao().get())
        assertEquals(countBefore, database.questionOccurrenceDao().count())
    }

    @Test
    fun matrixI_questionMappingMismatch_isUnsafe() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installQuestionMappingMismatch(database)

        val stateBefore = database.practiceStateDao().get()!!
        val result = runCatching { repository.syncEnvironmentAndReconcile() }
        assertTrue(result.isFailure)
        assertEquals(stateBefore, database.practiceStateDao().get())
    }

    @Test
    fun matrixK_answeredAtCursor_deletedAnswerText_advancesToSeven() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installDesyncScenario(
            database = database,
            existingAtCursorStatus = QuestionOccurrenceStatus.ANSWERED,
            includeStaleIncompleteAt5 = false,
            withAnswerAtCursor = false,
        )

        val occ6Before = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        assertNull(database.answerDao().getByOccurrenceId(occ6Before.id))
        assertNotNull(occ6Before.completedAtEpochMillis)

        repository.syncEnvironmentAndReconcile()

        val occ6After = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        assertEquals(occ6Before.id, occ6After.id)
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occ6After.status)
        assertEquals(7, database.practiceStateDao().get()!!.nextCyclePosition)
    }

    @Test
    fun matrixL_answeredAtCursorWithoutCompletedAt_isUnsafe() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installDesyncScenario(
            database = database,
            existingAtCursorStatus = QuestionOccurrenceStatus.ANSWERED,
            includeStaleIncompleteAt5 = false,
            withAnswerAtCursor = true,
        )

        val occ6 = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        database.questionOccurrenceDao().update(occ6.copy(completedAtEpochMillis = null))

        val stateBefore = database.practiceStateDao().get()!!
        val countBefore = database.questionOccurrenceDao().count()

        val result = runCatching { repository.syncEnvironmentAndReconcile() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CycleCorruptionException)

        assertEquals(stateBefore, database.practiceStateDao().get())
        assertEquals(countBefore, database.questionOccurrenceDao().count())
    }

    @Test
    fun matrixJ_consistentDatabase_noBehaviorChange() = runBlocking {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        timeProvider.setEpochMillis(practiceEpochAt(11, 0))
        repository.startPractice()

        val before = database.practiceStateDao().get()!!
        val occBefore = database.questionOccurrenceDao().count()

        timeProvider.setEpochMillis(practiceEpochAt(12, 0))
        assertEquals(CycleResult.ReconcileNoChanges, repository.syncEnvironmentAndReconcile())

        assertEquals(before, database.practiceStateDao().get())
        assertEquals(occBefore, database.questionOccurrenceDao().count())
    }

    private suspend fun runMatrixRecovery(
        existingAtCursorStatus: QuestionOccurrenceStatus,
        includeStaleIncompleteAt5: Boolean = false,
        withAnswerAtCursor: Boolean = false,
        expectedNext: Int,
    ) {
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installDesyncScenario(
            database = database,
            existingAtCursorStatus = existingAtCursorStatus,
            includeStaleIncompleteAt5 = includeStaleIncompleteAt5,
            withAnswerAtCursor = withAnswerAtCursor,
        )

        val occ6Before = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        val occ6Id = occ6Before.id

        repository.syncEnvironmentAndReconcile()

        val occ6After = database.questionOccurrenceDao().getByCycleAndPosition(1, 6)!!
        assertEquals(occ6Id, occ6After.id)
        assertEquals(expectedNext, database.practiceStateDao().get()!!.nextCyclePosition)
    }

    private fun practiceEpochAt(hour: Int, minute: Int): Long {
        return java.time.ZonedDateTime.of(
            2026, 8, 4, hour, minute, 0, 0,
            java.time.ZoneId.of(Vc6CursorDesyncFixtureSupport.ZONE_KIEV),
        ).toInstant().toEpochMilli()
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
