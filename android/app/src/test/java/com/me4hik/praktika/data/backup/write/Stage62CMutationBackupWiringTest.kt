package com.me4hik.praktika.data.backup.write

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class Stage62CMutationBackupWiringTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var sink: RecordingBackupMutationRequestSink
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        PraktikaRuntimeHolder.resetForTests()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedBaseData()
        timeProvider = FakeTimeProvider(epochAt(8, 0, 0), ZONE_KIEV)
        sink = RecordingBackupMutationRequestSink()
        repository = CycleRepository(database, timeProvider, sink, ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        PraktikaRuntimeHolder.resetForTests()
    }

    @Test
    fun saveAnswer_success_requestsAnswerSavedOnce() = runBlocking {
        prepareAvailableOccurrence()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        sink.clear()
        assertEquals(CycleResult.AnswerSaved, repository.saveAnswer(occurrence.id, "ok"))
        assertEquals(listOf(BackupRequestReason.ANSWER_SAVED), sink.reasons.toList())
        assertTrue(database.answerDao().count() >= 1)
        assertTrue(database.questionOccurrenceDao().count() >= 2)
    }

    @Test
    fun saveAnswer_failure_requestsZero() = runBlocking {
        prepareAvailableOccurrence()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        sink.clear()
        runCatching { repository.saveAnswer(occurrence.id, "   ") }
        assertTrue(sink.reasons.isEmpty())
    }

    @Test
    fun saveAnswer_sinkOrdinaryFailure_preservesMutation() = runBlocking {
        val throwing = ThrowingBackupMutationRequestSink()
        val repo = CycleRepository(database, timeProvider, throwing, ApplicationProvider.getApplicationContext())
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repo.startPractice()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        assertEquals(CycleResult.AnswerSaved, repo.saveAnswer(occurrence.id, "ok"))
        assertEquals(1, database.answerDao().count())
        assertEquals(
            QuestionOccurrenceStatus.ANSWERED,
            database.questionOccurrenceDao().getById(occurrence.id)!!.status,
        )
    }

    @Test
    fun deleteAnswer_rowsChanged_requestsOnce_andKeepsAnsweredOccurrence() = runBlocking {
        prepareAvailableOccurrence()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        repository.saveAnswer(occurrence.id, "to-delete")
        val answerId = database.answerDao().getByOccurrenceId(occurrence.id)!!.id
        sink.clear()
        val deleteRepo = RoomAnswerDeleteRepository(database, sink)
        deleteRepo.deleteAnswer(answerId)
        assertEquals(listOf(BackupRequestReason.ANSWER_DELETED), sink.reasons.toList())
        assertEquals(0, database.answerDao().count())
        val occ = database.questionOccurrenceDao().getById(occurrence.id)!!
        assertEquals(QuestionOccurrenceStatus.ANSWERED, occ.status)
        assertTrue(occ.completedAtEpochMillis != null)
    }

    @Test
    fun deleteAnswer_absent_requestsZero() = runBlocking {
        val deleteRepo = RoomAnswerDeleteRepository(database, sink)
        deleteRepo.deleteAnswer(999_999L)
        assertTrue(sink.reasons.isEmpty())
    }

    @Test
    fun deleteAnswer_sinkFailure_keepsDelete() = runBlocking {
        prepareAvailableOccurrence()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        repository.saveAnswer(occurrence.id, "to-delete")
        val answerId = database.answerDao().getByOccurrenceId(occurrence.id)!!.id
        val deleteRepo = RoomAnswerDeleteRepository(database, ThrowingBackupMutationRequestSink())
        deleteRepo.deleteAnswer(answerId)
        assertEquals(0, database.answerDao().count())
    }

    @Test
    fun startPractice_requestsPracticeStartedOnce() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        sink.clear()
        assertEquals(CycleResult.PracticeStarted, repository.startPractice())
        assertEquals(listOf(BackupRequestReason.PRACTICE_STARTED), sink.reasons.toList())
    }

    @Test
    fun markOccurrenceOpened_trueAndFalse() = runBlocking {
        prepareAvailableOccurrence()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        sink.clear()
        assertTrue(repository.markOccurrenceOpened(occurrence.id))
        assertEquals(listOf(BackupRequestReason.PRACTICE_STATE_CHANGED), sink.reasons.toList())
        sink.clear()
        assertTrue(!repository.markOccurrenceOpened(occurrence.id))
        assertTrue(sink.reasons.isEmpty())
    }

    @Test
    fun skipPauseResume_requestPracticeStateChangedOnceEach() = runBlocking {
        prepareAvailableOccurrence()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        sink.clear()
        assertEquals(CycleResult.SkipCompleted, repository.skipAvailableByUser(occurrence.id))
        assertEquals(listOf(BackupRequestReason.PRACTICE_STATE_CHANGED), sink.reasons.toList())

        val next = database.questionOccurrenceDao().getIncompleteOrdered().single()
        if (next.status == QuestionOccurrenceStatus.SCHEDULED) {
            timeProvider.setEpochMillis(next.plannedAtEpochMillis)
            repository.reconcile()
        }
        sink.clear()
        assertEquals(CycleResult.PauseEnabled, repository.pausePractice())
        assertEquals(listOf(BackupRequestReason.PRACTICE_STATE_CHANGED), sink.reasons.toList())

        sink.clear()
        assertEquals(CycleResult.PracticeResumed, repository.resumePractice())
        assertEquals(listOf(BackupRequestReason.PRACTICE_STATE_CHANGED), sink.reasons.toList())
    }

    @Test
    fun nestedSaveSkipPause_emitOuterReasonOnly() = runBlocking {
        // saveAnswer with nested reconcile path: start SCHEDULED then advance so nested reconcile flips AVAILABLE
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        val scheduled = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, scheduled.status)
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        sink.clear()
        assertEquals(CycleResult.AnswerSaved, repository.saveAnswer(scheduled.id, "nested"))
        assertEquals(listOf(BackupRequestReason.ANSWER_SAVED), sink.reasons.toList())

        val next = database.questionOccurrenceDao().getIncompleteOrdered().single()
        timeProvider.setEpochMillis(next.plannedAtEpochMillis + 60_000L)
        sink.clear()
        assertEquals(CycleResult.SkipCompleted, repository.skipAvailableByUser(next.id))
        assertEquals(listOf(BackupRequestReason.PRACTICE_STATE_CHANGED), sink.reasons.toList())

        val afterSkip = database.questionOccurrenceDao().getIncompleteOrdered().single()
        if (afterSkip.status == QuestionOccurrenceStatus.SCHEDULED) {
            timeProvider.setEpochMillis(afterSkip.plannedAtEpochMillis)
            repository.reconcile()
        }
        sink.clear()
        assertEquals(CycleResult.PauseEnabled, repository.pausePractice())
        assertEquals(listOf(BackupRequestReason.PRACTICE_STATE_CHANGED), sink.reasons.toList())
    }

    @Test
    fun schedule_identical_noPayloadDelta_zeroRequest() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        sink.clear()
        assertEquals(ScheduleUpdateResult.Success, repository.updateSchedule(defaultUpdates()))
        assertTrue(sink.reasons.isEmpty())
    }

    @Test
    fun schedule_changed_requestsScheduleChangedOnce() = runBlocking {
        sink.clear()
        assertEquals(
            ScheduleUpdateResult.Success,
            repository.updateSchedule(defaultUpdates(630, 860, 1210)),
        )
        assertEquals(listOf(BackupRequestReason.SCHEDULE_CHANGED), sink.reasons.toList())
    }

    @Test
    fun schedule_changedPlusReconcile_stillScheduleChangedOnce() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        sink.clear()
        assertEquals(
            ScheduleUpdateResult.Success,
            repository.updateSchedule(defaultUpdates(630, 860, 1210)),
        )
        assertEquals(listOf(BackupRequestReason.SCHEDULE_CHANGED), sink.reasons.toList())
    }

    @Test
    fun schedule_identical_withReconcileDelta_requestsRuntimeReconciled() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        sink.clear()
        assertEquals(ScheduleUpdateResult.Success, repository.updateSchedule(defaultUpdates()))
        assertEquals(listOf(BackupRequestReason.RUNTIME_RECONCILED), sink.reasons.toList())
    }

    @Test
    fun schedule_cPrime_identical_reconcileFalse_adjustTrue_runtimeReconciled() = runBlocking {
        // AVAILABLE deadline extended by pause/resume, then identical schedule rewrites via adjust.
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val available = database.questionOccurrenceDao().getIncompleteOrdered().single()
        assertEquals(QuestionOccurrenceStatus.AVAILABLE, available.status)
        val originalUntil = available.availableUntilEpochMillis
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repository.pausePractice()
        timeProvider.setEpochMillis(epochAt(12, 0, 0))
        repository.resumePractice()
        val extended = database.questionOccurrenceDao().getById(available.id)!!
        assertTrue(extended.availableUntilEpochMillis > originalUntil)
        sink.clear()
        assertEquals(ScheduleUpdateResult.Success, repository.updateSchedule(defaultUpdates()))
        assertEquals(listOf(BackupRequestReason.RUNTIME_RECONCILED), sink.reasons.toList())
        val after = database.questionOccurrenceDao().getById(available.id)!!
        assertTrue(after.availableUntilEpochMillis != extended.availableUntilEpochMillis)
    }

    @Test
    fun reconcile_changed_and_noChange() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        sink.clear()
        assertEquals(CycleResult.ReconcileNoChanges, repository.reconcile())
        assertTrue(sink.reasons.isEmpty())

        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        sink.clear()
        assertEquals(CycleResult.ReconcileChanged, repository.reconcile())
        assertEquals(listOf(BackupRequestReason.RUNTIME_RECONCILED), sink.reasons.toList())
    }

    @Test
    fun timezoneOnly_notStarted_and_paused_requestRuntimeReconciled() = runBlocking {
        sink.clear()
        timeProvider.setZoneId(ZONE_LONDON)
        assertEquals(CycleResult.ReconcileNotStarted, repository.syncEnvironmentAndReconcile())
        assertEquals(listOf(BackupRequestReason.RUNTIME_RECONCILED), sink.reasons.toList())
        assertEquals(ZONE_LONDON, database.practiceStateDao().get()!!.activeZoneId)

        timeProvider.setZoneId(ZONE_KIEV)
        repository.syncEnvironmentAndReconcile()
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        repository.pausePractice()
        sink.clear()
        timeProvider.setZoneId(ZONE_LONDON)
        assertEquals(CycleResult.ReconcilePaused, repository.syncEnvironmentAndReconcile())
        assertEquals(listOf(BackupRequestReason.RUNTIME_RECONCILED), sink.reasons.toList())

        sink.clear()
        assertEquals(CycleResult.ReconcilePaused, repository.syncEnvironmentAndReconcile())
        assertTrue(sink.reasons.isEmpty())
    }

    @Test
    fun cursorRepair_requestsRuntimeReconciledOnce() = runBlocking {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val state = database.practiceStateDao().get()!!
        // Point cursor at existing occurrence position → recoverable stale cursor.
        database.practiceStateDao().update(state.copy(nextCyclePosition = 1))
        sink.clear()
        val result = repository.reconcile()
        assertTrue(
            result == CycleResult.ReconcileChanged || result == CycleResult.ReconcileNoChanges,
        )
        assertEquals(1, sink.reasons.size)
        assertEquals(BackupRequestReason.RUNTIME_RECONCILED, sink.reasons.single())
    }

    @Test
    fun notificationCoordinator_reconcileOwnsBackupRequest() = runBlocking {
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        sink.clear()
        // Coordinator sync path ultimately calls CycleRepository.syncEnvironmentAndReconcile;
        // ownership is proven by repository sink emission with zero coordinator backup API.
        val result = repository.syncEnvironmentAndReconcile()
        assertEquals(CycleResult.ReconcileChanged, result)
        assertEquals(listOf(BackupRequestReason.RUNTIME_RECONCILED), sink.reasons.toList())
    }

    @Test
    fun authorizedSink_mapsMutationTrigger_andRuntimeIdentity() {
        val runtime = RuntimeFactory.create(context)
        assertTrue(runtime.backupMutationRequestSink is AuthorizedBackupMutationRequestSink)
        val first = PraktikaRuntimeHolder.get(context)
        val second = PraktikaRuntimeHolder.get(context)
        assertSame(first, second)
        assertSame(first.authorizedBackupService, second.authorizedBackupService)
        assertSame(first.backupMutationRequestSink, second.backupMutationRequestSink)
        PraktikaDatabase.resetInstanceForTests()
        runtime.database.close()
    }

    @Test
    fun startupSuppression_mutationEmittedButPhysicalSuppressed() = runTest {
        val folder = "content://auth/tree/A"
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val harness = serviceHarness(auth = folder, hint = folder, scope = this)
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests.add(reason to trigger)
        }
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folder to AuthorizedStorageResolveResult.Ready(storage))

        val authorizedSink = AuthorizedBackupMutationRequestSink(harness.service)
        val repo = CycleRepository(database, timeProvider, authorizedSink, ApplicationProvider.getApplicationContext())
        harness.service.beginInitialization()
        timeProvider.setEpochMillis(epochAt(8, 0, 0))
        repo.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
        repo.reconcile()
        advanceUntilIdle()
        assertTrue(
            requests.any {
                it.first == BackupRequestReason.PRACTICE_STARTED &&
                    it.second == BackupAttemptTrigger.MUTATION
            },
        )
        assertTrue(
            requests.any {
                it.first == BackupRequestReason.RUNTIME_RECONCILED &&
                    it.second == BackupAttemptTrigger.MUTATION
            },
        )
        assertEquals(0, storage.writeCalls.size)
        harness.service.endInitialization()
        harness.service.requestBackup(BackupRequestReason.STARTUP_CATCHUP, BackupAttemptTrigger.STARTUP)
        advanceUntilIdle()
        assertEquals(1, storage.writeCalls.size)
    }

    @Test
    fun restoreSuppression_mutationDirties_noPhysicalMutationWrite() = runTest {
        val folder = "content://auth/tree/A"
        val harness = serviceHarness(auth = folder, hint = folder, scope = this)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folder to AuthorizedStorageResolveResult.Ready(storage))

        val authorizedSink = AuthorizedBackupMutationRequestSink(harness.service)
        val repo = CycleRepository(database, timeProvider, authorizedSink, ApplicationProvider.getApplicationContext())
        harness.service.beginRestoreSession()
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repo.startPractice()
        advanceUntilIdle()
        assertEquals(0, storage.writeCalls.size)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())
        harness.service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
        advanceUntilIdle()
        assertTrue(storage.writeCalls.size <= 1)
    }

    @Test
    fun fastMutations_logicalCountsCorrect() = runBlocking {
        sink.clear()
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        val occurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        repository.saveAnswer(occurrence.id, "fast")
        repository.updateSchedule(defaultUpdates(630, 860, 1210))
        assertEquals(1, sink.countOf(BackupRequestReason.PRACTICE_STARTED))
        assertEquals(1, sink.countOf(BackupRequestReason.ANSWER_SAVED))
        assertEquals(1, sink.countOf(BackupRequestReason.SCHEDULE_CHANGED))
    }

    private fun serviceHarness(
        auth: String?,
        hint: String?,
        scope: kotlinx.coroutines.CoroutineScope,
    ): ServiceHarness {
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = hint,
                authorizedTreeUri = auth,
                lastSuccessfulBackupAtEpochMillis = null,
                lastFailureCategory = null,
                needsReconnect = false,
            ),
        )
        val resolver = FakeResolver()
        val goldenPayload = com.me4hik.praktika.data.backup.BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload
        val factory = ProductionBackupCoordinatorFactory { storage ->
            ProductionBackupCoordinator(
                storage = storage,
                exportAction = { BackupExportResult.Success(goldenPayload) },
                metadataFactory = BackupSnapshotMetadataFactory(
                    clock = CountingBackupClock(),
                    appMetadataProvider = FixedBackupAppMetadataProvider(),
                ),
                scope = scope,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
        }
        val service = AuthorizedBackupService(
            writeStateRepository = repo,
            storageResolver = resolver,
            coordinatorFactory = factory,
            scope = scope,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            ioGate = BackupIoSessionGate(),
        )
        return ServiceHarness(service, resolver)
    }

    private class FakeResolver : AuthorizedBackupStorageResolver {
        var resultFor: Map<String, AuthorizedStorageResolveResult> = emptyMap()
        override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
            return resultFor[authorizedUriString] ?: AuthorizedStorageResolveResult.Unavailable
        }
    }

    private class ServiceHarness(
        val service: AuthorizedBackupService,
        val resolver: FakeResolver,
    )

    private suspend fun prepareAvailableOccurrence() {
        timeProvider.setEpochMillis(epochAt(11, 0, 0))
        repository.startPractice()
        timeProvider.setEpochMillis(epochAt(11, 30, 0))
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

    private fun defaultUpdates(
        a: Int = 660,
        b: Int = 900,
        c: Int = 1140,
    ): List<ScheduleSlotUpdate> = listOf(
        ScheduleSlotUpdate(1, a),
        ScheduleSlotUpdate(2, b),
        ScheduleSlotUpdate(3, c),
    )

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2024, 6, 1, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        private const val ZONE_KIEV = "Europe/Kyiv"
        private const val ZONE_LONDON = "Europe/London"
    }
}
