// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik START - post-restore runtime compatibility
package com.me4hik.praktika.data.backup.restore

import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRestoreFixturesShapeTest {
    @Test
    fun richFixture_hasSingleIncompleteAtCursor() {
        val payload = BackupRestoreFixtures.richPayload()
        val incomplete = payload.occurrences.filter {
            it.status == QuestionOccurrenceStatus.SCHEDULED.name ||
                it.status == QuestionOccurrenceStatus.AVAILABLE.name
        }
        assertEquals(1, incomplete.size)
        assertEquals(1 to 4, incomplete.single().cycleNumber to incomplete.single().cyclePosition)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED.name, incomplete.single().status)
    }

    @Test
    fun richFixture_hasNoCrossCycleAnomaly() {
        val payload = BackupRestoreFixtures.richPayload()
        assertFalse(payload.occurrences.any { it.cycleNumber > 1 })
        assertEquals(4, payload.practiceState.nextCyclePosition)
        assertEquals(1, payload.practiceState.currentCycleNumber)
    }

    @Test
    fun richFixture_deletedTextCountIsOne() {
        val evidence = PostRestoreVerificationEvidence.fromPayload(BackupRestoreFixtures.richPayload())
        assertEquals(1, evidence.deletedTextCount)
        assertEquals(2, evidence.answeredCount)
        assertEquals(1, evidence.skippedCount)
        assertEquals(1, evidence.scheduledCount)
        assertEquals(0, evidence.missedCount)
    }
}

class PostRestoreReconcileIntegrationTest : RestoreRoomTestSupport() {
    private lateinit var exporter: RoomBackupExporter
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var cycleRepository: CycleRepository
    private lateinit var practiceReadRepository: RoomPracticeReadRepository

    @Before
    fun setUpIntegration() {
        setUpDatabase()
        exporter = RoomBackupExporter(database)
        timeProvider = FakeTimeProvider(
            epochMillis = BackupRestoreFixtures.acceptanceReconcileNowMillis(),
            zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
        cycleRepository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        practiceReadRepository = RoomPracticeReadRepository(database)
    }

    @After
    fun tearDownIntegration() {
        tearDownDatabase()
    }

    @Test
    fun restoreRichFixture_roundtripMatchesBeforeReconcile() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))

        assertRestoredStateBeforeReconcile()

        when (val export = exporter.export()) {
            is BackupExportResult.Success -> {
                assertTrue(
                    BackupPayloadSemanticComparator.equalsSemantically(
                        envelope.payload,
                        export.payload,
                    ),
                )
            }
            else -> error("Unexpected export result: $export")
        }
    }

    @Test
    fun restoreRichFixture_reconcileAndReadSnapshot_areRuntimeCompatible() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope()
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))
        assertRestoredStateBeforeReconcile()

        val reconcileResult = cycleRepository.syncEnvironmentAndReconcile()
        assertTrue(reconcileResult is CycleResult.ReconcileNoChanges || reconcileResult is CycleResult.ReconcileChanged)

        val snapshot = practiceReadRepository.observeSnapshot().first()
        assertTrue(snapshot.practiceState.isPracticeStarted)
        assertNotNull(snapshot.incompleteOccurrence)
        assertEquals(1, snapshot.incompleteOccurrence!!.cycleNumber)
        assertEquals(4, snapshot.incompleteOccurrence!!.cyclePosition)
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, snapshot.incompleteOccurrence!!.status)
    }

    @Test
    fun restoreRichFixture_deletedTextSemanticsSurviveRuntimeRead() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())
        cycleRepository.syncEnvironmentAndReconcile()

        val deletedTextOccurrence = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, deletedTextOccurrence.status)
        assertNull(database.answerDao().getByOccurrenceId(deletedTextOccurrence.id))

        practiceReadRepository.observeSnapshot().first()
        Unit
    }

    private suspend fun assertRestoredStateBeforeReconcile() {
        assertEquals(listOf(900, 660, 1140), scheduleMinutes())
        assertEquals(4, database.questionOccurrenceDao().count())
        assertEquals(1, database.answerDao().count())
        assertEquals(1, database.questionOccurrenceDao().getIncompleteOrdered().size)

        val state = database.practiceStateDao().get()!!
        assertTrue(state.isPracticeStarted)
        assertEquals(1, state.currentCycleNumber)
        assertEquals(4, state.nextCyclePosition)

        val current = database.questionOccurrenceDao().getByCycleAndPosition(1, 4)!!
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, current.status)

        val deletedText = database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, deletedText.status)
        assertNull(database.answerDao().getByOccurrenceId(deletedText.id))
    }
}

class ZeroIncompleteRuntimeContractTest : RestoreRoomTestSupport() {
    private lateinit var cycleRepository: CycleRepository
    private lateinit var practiceReadRepository: RoomPracticeReadRepository

    @Before
    fun setUpZeroIncomplete() {
        setUpDatabase()
        val timeProvider = FakeTimeProvider(
            epochMillis = BackupRestoreFixtures.acceptanceReconcileNowMillis(),
            zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
        cycleRepository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
        practiceReadRepository = RoomPracticeReadRepository(database)
    }

    @After
    fun tearDownZeroIncomplete() {
        tearDownDatabase()
    }

    @Test
    fun startedWithZeroIncomplete_reconcileToleratesButReadSnapshotThrows() = runBlocking {
        val basePayload = BackupRestoreFixtures.richPayload()
        val zeroIncompletePayload = PraktikaBackupPayload(
            practiceState = basePayload.practiceState,
            scheduleSlots = basePayload.scheduleSlots,
            occurrences = BackupRestoreFixtures.richOccurrences().filterNot {
                it.cycleNumber == 1 && it.cyclePosition == 4
            },
            answers = basePayload.answers,
        )
        val envelope = BackupRestoreFixtures.envelope(zeroIncompletePayload)
        assertEquals(BackupRestoreResult.Success, restorer.restore(envelope))
        assertEquals(0, database.questionOccurrenceDao().getIncompleteOrdered().size)

        val reconcileResult = cycleRepository.syncEnvironmentAndReconcile()
        assertTrue(reconcileResult is CycleResult.ReconcileNoChanges || reconcileResult is CycleResult.ReconcileChanged)

        val thrown = runCatching {
            practiceReadRepository.observeSnapshot().first()
        }.exceptionOrNull()
        assertTrue(thrown is CycleCorruptionException)
        assertEquals(
            "Practice is started but no incomplete occurrence exists",
            thrown!!.message,
        )
    }
}
// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik END
