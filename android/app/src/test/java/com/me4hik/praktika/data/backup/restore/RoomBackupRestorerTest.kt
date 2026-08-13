// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1 Room integration tests
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomBackupRestorerTest : RestoreRoomTestSupport() {
    @Before
    fun setUp() {
        setUpDatabase()
    }

    @After
    fun tearDown() {
        tearDownDatabase()
    }

    @Test
    fun restore_freshSeededDatabase_success() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))
        assertTrue(database.practiceStateDao().get()!!.isPracticeStarted)
        assertEquals(4, database.questionOccurrenceDao().count())
        assertEquals(1, database.answerDao().count())
    }

    @Test
    fun restore_customScheduleWithOldValueCollision_succeeds() = runBlocking {
        assertEquals(BackupRestoreFixtures.defaultTargetScheduleMinutes, scheduleMinutes())

        val result = restorer.restore(BackupRestoreFixtures.richEnvelope())
        assertEquals(BackupRestoreResult.Success, result)
        assertEquals(listOf(900, 660, 1140), scheduleMinutes())
    }

    @Test
    fun restore_occurrenceIdsGeneratedAndAnswersMappedCorrectly() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val answer = database.answerDao().getByOccurrenceId(occurrence.id)!!
        assertEquals("Answer preserved exactly", answer.text)
        assertEquals(1_700_000_110_000L, answer.createdAtEpochMillis)
        assertNotEquals(0L, occurrence.id)
    }

    @Test
    fun restore_answeredWithoutAnswer_preserved() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occurrence.status)
        assertNull(database.answerDao().getByOccurrenceId(occurrence.id))
    }

    @Test
    fun restore_answeredWithAnswer_preserved() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        val answer = database.answerDao().getByOccurrenceId(occurrence.id)
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occurrence.status)
        assertEquals("Answer preserved exactly", answer!!.text)
    }

    @Test
    fun restore_allTimestampsPreserved() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())

        val state = database.practiceStateDao().get()!!
        assertEquals(1_700_000_000_000L, state.practiceStartedAtEpochMillis)
        assertEquals(1_700_000_250_000L, state.lastProcessedAtEpochMillis)

        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        assertEquals(1_700_000_050_000L, occurrence.openedAtEpochMillis)
        assertEquals(1_700_000_100_000L, occurrence.completedAtEpochMillis)
    }

    @Test
    fun restore_allZonesPreserved() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())

        val state = database.practiceStateDao().get()!!
        assertEquals(BackupRestoreFixtures.ZONE_MOSCOW, state.activeZoneId)
        val occurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 4)!!
        assertEquals(BackupRestoreFixtures.ZONE_MOSCOW, occurrence.zoneId)
    }

    @Test
    fun restore_targetHasOccurrence_refusesWithoutMutation() = runBlocking {
        database.questionOccurrenceDao().insert(sampleOccurrence())

        val beforeSchedule = scheduleMinutes()
        val beforeState = database.practiceStateDao().get()

        val result = restorer.restore(BackupRestoreFixtures.richEnvelope())
        assertEquals(BackupRestoreResult.TargetNotEmpty, result)
        assertEquals(beforeSchedule, scheduleMinutes())
        assertEquals(beforeState, database.practiceStateDao().get())
        assertEquals(1, database.questionOccurrenceDao().count())
    }

    @Test
    fun restore_targetStarted_refusesWithoutMutation() = runBlocking {
        database.practiceStateDao().upsert(
            database.practiceStateDao().get()!!.copy(isPracticeStarted = true),
        )

        val beforeSchedule = scheduleMinutes()
        val result = restorer.restore(BackupRestoreFixtures.richEnvelope())
        assertEquals(BackupRestoreResult.TargetNotEmpty, result)
        assertEquals(beforeSchedule, scheduleMinutes())
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun restore_secondRestore_refusesTargetNotEmpty() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))
        assertEquals(BackupRestoreResult.TargetNotEmpty, restorer.restore(envelope))
    }

    @Test
    fun restore_scheduleMutationRollbackOnFailure() = runBlocking {
        restorer.transactionProbe = RestoreTransactionProbe(
            afterSchedule = {
                throw RestoreAbortException(
                    BackupRestoreResult.PostValidationFailure(
                        reason = PostValidationFailureReason.PAYLOAD_SEMANTIC_MISMATCH,
                    ),
                )
            },
        )

        val result = restorer.restore(BackupRestoreFixtures.richEnvelope())
        assertTrue(result is BackupRestoreResult.PostValidationFailure)
        assertEquals(BackupRestoreFixtures.defaultTargetScheduleMinutes, scheduleMinutes())
        assertEquals(0, database.questionOccurrenceDao().count())
        assertEquals(0, database.answerDao().count())
        assertFalse(database.practiceStateDao().get()!!.isPracticeStarted)
    }

    @Test
    fun restore_runtimeSnapshotMatchesInputBeforeCommit() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))
        val export = RoomBackupExporter(database).export()
        assertTrue(export is BackupExportResult.Success)
        assertTrue(
            BackupPayloadSemanticComparator.equalsSemantically(
                envelope.payload,
                (export as BackupExportResult.Success).payload,
            ),
        )
    }

    @Test
    fun restore_postCommitExporterRoundtripMatchesInput() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))

        when (val export = RoomBackupExporter(database).export()) {
            is BackupExportResult.Success -> {
                assertTrue(
                    BackupPayloadSemanticComparator.equalsSemantically(
                        envelope.payload,
                        export.payload,
                    ),
                )
            }

            else -> throw AssertionError("Expected export success, got $export")
        }
    }

    @Test
    fun restore_seedMismatch_refusesWithoutMutation() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope(seedVersion = 2)
        val beforeSchedule = scheduleMinutes()

        val result = restorer.restore(envelope)
        assertTrue(result is BackupRestoreResult.IncompatibleSeed)
        val incompatible = result as BackupRestoreResult.IncompatibleSeed
        assertEquals(2, incompatible.backupSeed)
        assertEquals(1, incompatible.targetSeed)
        assertEquals(
            IncompatibleSeedClassification.NEWER_THAN_INSTALLED,
            incompatible.classification,
        )
        assertEquals(beforeSchedule, scheduleMinutes())
    }

    @Test
    fun restore_staticQuestionMismatch_refuses() = runBlocking {
        val questions = database.questionDao().getAllOrderedByCyclePosition().map { question ->
            when (question.id) {
                5 -> question.copy(cyclePosition = 6)
                6 -> question.copy(cyclePosition = 5)
                else -> question
            }
        }
        database.questionDao().deleteAll()
        database.questionDao().insertAll(questions)

        val beforeSchedule = scheduleMinutes()
        val payload = PraktikaBackupPayload(
            practiceState = BackupRestoreFixtures.notStartedPayload().practiceState,
            scheduleSlots = BackupRestoreFixtures.customAscendingSchedule,
            occurrences = listOf(
                com.me4hik.praktika.data.backup.model.BackupOccurrence(
                    questionId = 5,
                    questionTextSnapshot = "Question five snapshot",
                    cycleNumber = 1,
                    cyclePosition = 5,
                    scheduleSlotIndex = 2,
                    plannedAtEpochMillis = 1_700_000_300_000L,
                    availableUntilEpochMillis = 1_700_003_800_000L,
                    openedAtEpochMillis = null,
                    completedAtEpochMillis = 1_700_000_350_000L,
                    status = QuestionOccurrenceStatus.SKIPPED_BY_USER.name,
                    zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
                ),
            ),
            answers = emptyList(),
        )
        val envelope = BackupRestoreFixtures.envelope(payload)

        val result = restorer.restore(envelope)
        assertTrue(result is BackupRestoreResult.StaticQuestionMismatch)
        assertEquals(beforeSchedule, scheduleMinutes())
        assertEquals(0, database.questionOccurrenceDao().count())
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun restore_cursorConsistencyAccepted() = runBlocking {
        assertEquals(BackupRestoreResult.Success, restorer.restore(BackupRestoreFixtures.richEnvelope()))
        val state = database.practiceStateDao().get()!!
        assertTrue(state.isPracticeStarted)
        assertEquals(1, state.currentCycleNumber)
        assertEquals(4, state.nextCyclePosition)
    }

    @Test
    fun restore_cancellationPropagates() = runBlocking {
        restorer.transactionProbe = RestoreTransactionProbe(
            afterSchedule = { throw CancellationException("cancelled") },
        )

        val thrown = runCatching {
            restorer.restore(BackupRestoreFixtures.richEnvelope())
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(BackupRestoreFixtures.defaultTargetScheduleMinutes, scheduleMinutes())
    }

    private fun sampleOccurrence(): QuestionOccurrenceEntity {
        return QuestionOccurrenceEntity(
            questionId = 1,
            questionTextSnapshot = "seed occurrence",
            cycleNumber = 1,
            cyclePosition = 1,
            scheduleSlotIndex = 1,
            plannedAtEpochMillis = 1_700_000_000_000L,
            availableUntilEpochMillis = 1_700_003_600_000L,
            openedAtEpochMillis = null,
            completedAtEpochMillis = 1_700_000_100_000L,
            status = QuestionOccurrenceStatus.ANSWERED,
            zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
