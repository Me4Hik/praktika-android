// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator tests
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows

class BackupRestoreCoordinatorTest : RestoreRoomTestSupport() {
    @Before
    fun setUp() {
        setUpDatabase()
    }

    @After
    fun tearDown() {
        tearDownDatabase()
    }

    @Test
    fun coordinator_bothValid_selectsB() = runBlocking {
        val storage = FakeBackupStorage(
            slotA = validEnvelope(BackupSlotId.A, BackupRestoreFixtures.notStartedPayloadEnvelope(1L)),
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator()

        val result = coordinator.inspectLatest(storage)

        assertTrue(result is BackupRestoreCoordinatorResult.PreviewReady)
        val preview = (result as BackupRestoreCoordinatorResult.PreviewReady).preview
        assertEquals(BackupSlotId.B, preview.selectedSlot)
        assertTrue(preview.isPracticeStarted)
        assertEquals(1, preview.answerCount)
        assertEquals(1, preview.deletedTextCount)
        assertEquals(4, preview.occurrenceCount)
    }

    @Test
    fun coordinator_oneValid_selectsIt() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator()

        val result = coordinator.inspectLatest(storage)

        assertTrue(result is BackupRestoreCoordinatorResult.PreviewReady)
        assertEquals(BackupSlotId.B, (result as BackupRestoreCoordinatorResult.PreviewReady).preview.selectedSlot)
    }

    @Test
    fun coordinator_noValid_returnsNoBackup() = runBlocking {
        val coordinator = createCoordinator()
        val result = coordinator.inspectLatest(FakeBackupStorage())
        assertEquals(BackupRestoreCoordinatorResult.NoValidBackup, result)
    }

    @Test
    fun restore_reinspectsBeforeExecute() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val recording = RecordingRestoreEngine()
        val coordinator = createCoordinator(restorer = recording)
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        storage.inspectCount = 0
        coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(storage.inspectCount >= 1)
    }

    @Test
    fun restore_sameIdentity_executes() = runBlocking {
        val envelope = BackupRestoreFixtures.richEnvelope().withSequence(2L)
        val storage = FakeBackupStorage(slotB = validEnvelope(BackupSlotId.B, envelope))
        val recording = RecordingRestoreEngine(RoomBackupRestorer(database))
        val coordinator = createCoordinator(restorer = recording)
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.FullSuccess)
        assertEquals(1, recording.callCount)
    }

    @Test
    fun restore_previewStale_sequenceChanged_aborts() = runBlocking {
        val storage = MutableBackupStorage(
            delegate = FakeBackupStorage(
                slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
            ),
        )
        val recording = RecordingRestoreEngine()
        val coordinator = createCoordinator(restorer = recording)
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        storage.mutateSequence(3L)
        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.PreviewStale)
        assertEquals(0, recording.callCount)
    }

    @Test
    fun restore_previewStale_checksumChanged_aborts() = runBlocking {
        val storage = MutableBackupStorage(
            delegate = FakeBackupStorage(
                slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
            ),
        )
        val recording = RecordingRestoreEngine()
        val coordinator = createCoordinator(restorer = recording)
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        storage.mutateChecksum("different-checksum-value-that-is-long-enough-0123456789abcdef0123456789ab")
        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.PreviewStale)
        assertEquals(0, recording.callCount)
    }

    @Test
    fun restore_previewStale_latestSlotChanged_aborts() = runBlocking {
        val storage = MutableBackupStorage(
            delegate = FakeBackupStorage(
                slotA = validEnvelope(BackupSlotId.A, BackupRestoreFixtures.notStartedPayloadEnvelope(1L)),
                slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
            ),
        )
        val recording = RecordingRestoreEngine()
        val coordinator = createCoordinator(restorer = recording)
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        storage.replaceWith(
            FakeBackupStorage(
                slotA = validEnvelope(BackupSlotId.A, BackupRestoreFixtures.notStartedPayloadEnvelope(5L)),
            ),
        )
        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.PreviewStale)
        assertEquals(0, recording.callCount)
    }

    @Test
    fun restore_forwardsCoreTargetNotEmpty() = runBlocking {
        runBlocking { restorer.restore(BackupRestoreFixtures.richEnvelope()) }
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator()
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.RestoreCoreFailure)
        assertEquals(
            BackupRestoreResult.TargetNotEmpty,
            (result as BackupRestoreCoordinatorResult.RestoreCoreFailure).restoreResult,
        )
    }

    @Test
    fun restore_success_runsVerifierBeforeReconcile() = runBlocking {
        val order = mutableListOf<String>()
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            verifier = object : PostRestoreDataVerifier {
                override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
                    order += "verify"
                    return PostRestoreVerificationResult.Success()
                }
            },
            reconcileAction = {
                order += "reconcile"
                CycleResult.ReconcileNoChanges
            },
            notificationSyncAction = {
                order += "notification"
                NotificationSyncObservation.Invoked
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertEquals(listOf("verify", "reconcile", "notification"), order)
    }

    @Test
    fun restore_verifierFailure_doesNotRunReconcile() = runBlocking {
        val order = mutableListOf<String>()
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            verifier = object : PostRestoreDataVerifier {
                override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
                    order += "verify"
                    return PostRestoreVerificationResult.Mismatch("test")
                }
            },
            reconcileAction = {
                order += "reconcile"
                CycleResult.ReconcileNoChanges
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.RestoreDataSuccessVerificationFailure)
        assertEquals(listOf("verify"), order)
        assertTrue(database.practiceStateDao().get()!!.isPracticeStarted)
    }

    @Test
    fun restore_success_runsReconcileAfterVerifier() = runBlocking {
        var reconcileCalled = false
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            verifier = object : PostRestoreDataVerifier {
                override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
                    return PostRestoreVerificationResult.Success()
                }
            },
            reconcileAction = {
                reconcileCalled = true
                CycleResult.ReconcileChanged
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.FullSuccess)
        assertTrue(reconcileCalled)
    }

    @Test
    fun restore_reconcileFailure_keepsRestoreCommitted() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            reconcileAction = { throw IllegalStateException("reconcile_failed") },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning)
        assertTrue(database.practiceStateDao().get()!!.isPracticeStarted)
    }

    @Test
    fun restore_notificationSyncOnlyAfterReconcile() = runBlocking {
        val order = mutableListOf<String>()
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            reconcileAction = {
                order += "reconcile"
                CycleResult.ReconcileNoChanges
            },
            notificationSyncAction = {
                order += "notification"
                NotificationSyncObservation.Invoked
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertEquals(listOf("reconcile", "notification"), order)
    }

    @Test
    fun restore_cancellationPropagates() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = object : BackupRestoreEngine {
                override suspend fun restore(envelope: PraktikaBackupEnvelope): BackupRestoreResult {
                    throw CancellationException("cancelled")
                }
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val thrown = runCatching {
            coordinator.executeRestore(storage, preview.identity, preview.preview)
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun targetEligibility_freshSeededRoom_restoreAvailable() = runBlocking {
        val coordinator = createCoordinator()
        assertEquals(RestoreTargetEligibility.RestoreAvailable, coordinator.checkTargetEligibility())
    }

    @Test
    fun targetEligibility_startedTarget_targetNotEmpty() = runBlocking {
        restorer.restore(BackupRestoreFixtures.richEnvelope())
        val coordinator = createCoordinator()
        assertEquals(RestoreTargetEligibility.TargetNotEmpty, coordinator.checkTargetEligibility())
    }

    @Test
    fun targetEligibility_existingOccurrence_targetNotEmpty() = runBlocking {
        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "x",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1L,
                availableUntilEpochMillis = 2L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.SCHEDULED,
                zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
            ),
        )
        val coordinator = createCoordinator()
        assertEquals(RestoreTargetEligibility.TargetNotEmpty, coordinator.checkTargetEligibility())
    }

    @Test
    fun deviceVerifier_correctedFixture_returnsExplicitPass() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val exporter = RoomBackupExporter(database)
        val verifier = RecordingPostRestoreVerifier(
            delegate = DeviceRoundtripVerifier(exporter),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            verifier = verifier,
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.FullSuccess)
        val verification = (result as BackupRestoreCoordinatorResult.FullSuccess).verification
        assertTrue(verification is PostRestoreVerificationResult.Success)
        val evidence = (verification as PostRestoreVerificationResult.Success).evidence!!
        assertEquals(listOf(900, 660, 1140), evidence.scheduleMinutes)
        assertEquals(4, evidence.occurrenceCount)
        assertEquals(1, evidence.answerCount)
        assertEquals(1, evidence.deletedTextCount)
        assertEquals(2, evidence.answeredCount)
        assertEquals(1, evidence.skippedCount)
        assertEquals(1, evidence.scheduledCount)
        assertEquals(0, evidence.missedCount)
        assertEquals(1, evidence.currentCycleNumber)
        assertEquals(4, evidence.nextCyclePosition)
    }

    @Test
    fun runtimeSyncFailure_preservesVerifierPassEvidence() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val exporter = RoomBackupExporter(database)
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            verifier = DeviceRoundtripVerifier(exporter),
            reconcileAction = { throw IllegalStateException("reconcile_failed") },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning)
        val warning = result as BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning
        assertTrue(warning.dataRestoreCommitted)
        assertTrue(warning.verification is PostRestoreVerificationResult.Success)
        assertNotNull((warning.verification as PostRestoreVerificationResult.Success).evidence)
        assertTrue(database.practiceStateDao().get()!!.isPracticeStarted)
    }

    @Test
    fun reconcileFailure_exposesFailureClass() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            reconcileAction = { throw IllegalStateException("reconcile_failed_detail") },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning)
        val warning = result as BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning
        assertEquals(IllegalStateException::class.java.name, warning.reconcileFailureClassName)
        assertEquals("reconcile_failed_detail", warning.reconcileFailureMessage)
    }

    @Test
    fun restore_reconcileFailure_doesNotRunNotification() = runBlocking {
        val order = mutableListOf<String>()
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            reconcileAction = {
                order += "reconcile"
                throw IllegalStateException("reconcile_failed")
            },
            notificationSyncAction = {
                order += "notification"
                NotificationSyncObservation.Invoked
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val result = coordinator.executeRestore(storage, preview.identity, preview.preview)

        assertTrue(result is BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning)
        assertEquals(listOf("reconcile"), order)
    }

    @Test
    fun executeRestore_cancellationDuringRuntimeSync_isRethrown() = runBlocking {
        val order = mutableListOf<String>()
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            reconcileAction = {
                order += "reconcile"
                throw CancellationException("cancelled during reconcile")
            },
            notificationSyncAction = {
                order += "notification"
                NotificationSyncObservation.Invoked
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val thrown = runCatching {
            coordinator.executeRestore(storage, preview.identity, preview.preview)
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(listOf("reconcile"), order)
    }

    @Test
    fun executeRestore_runtimeActionsExecuteOffAndroidMainWhenCallerIsMain() = runBlocking {
        val storage = FakeBackupStorage(
            slotB = validEnvelope(BackupSlotId.B, BackupRestoreFixtures.richEnvelope().withSequence(2L)),
        )
        val mainLooper = android.os.Looper.getMainLooper()
        var reconcileOnMain = false
        var notificationOnMain = false
        val coordinator = createCoordinator(
            restorer = RoomBackupRestorer(database),
            reconcileAction = {
                reconcileOnMain = android.os.Looper.myLooper() == mainLooper
                CycleResult.ReconcileNoChanges
            },
            notificationSyncAction = {
                notificationOnMain = android.os.Looper.myLooper() == mainLooper
                NotificationSyncObservation.Invoked
            },
        )
        val preview = coordinator.inspectLatest(storage) as BackupRestoreCoordinatorResult.PreviewReady

        val resultDeferred = async(Dispatchers.Main) {
            coordinator.executeRestore(storage, preview.identity, preview.preview)
        }
        while (resultDeferred.isActive) {
            Shadows.shadowOf(mainLooper).idle()
            delay(1)
        }
        resultDeferred.await()

        assertFalse(reconcileOnMain)
        assertFalse(notificationOnMain)
    }

    private fun createCoordinator(
        restorer: BackupRestoreEngine = RecordingRestoreEngine(RoomBackupRestorer(database)),
        verifier: PostRestoreDataVerifier = NoOpPostRestoreDataVerifier,
        reconcileAction: suspend () -> CycleResult = { CycleResult.ReconcileNoChanges },
        notificationSyncAction: suspend () -> NotificationSyncObservation = {
            NotificationSyncObservation.Invoked
        },
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    ): BackupRestoreCoordinator {
        return BackupRestoreCoordinator(
            database = database,
            restorer = restorer,
            postRestoreVerifier = verifier,
            ioDispatcher = ioDispatcher,
            reconcileAction = reconcileAction,
            notificationSyncAction = notificationSyncAction,
        )
    }

    private fun validEnvelope(slot: BackupSlotId, envelope: PraktikaBackupEnvelope): SlotReadResult.Valid {
        return SlotReadResult.Valid(slot = slot, envelope = envelope)
    }
}

private fun PraktikaBackupEnvelope.withSequence(sequence: Long): PraktikaBackupEnvelope {
    return PraktikaBackupEnvelope(
        backupSchemaVersion = backupSchemaVersion,
        backupSequence = sequence,
        createdAtEpochMillis = createdAtEpochMillis,
        sourceAppVersionCode = sourceAppVersionCode,
        sourceAppVersionName = sourceAppVersionName,
        sourceSeedVersion = sourceSeedVersion,
        backupChecksumSha256 = backupChecksumSha256,
        payload = payload,
    )
}

private fun BackupRestoreFixtures.notStartedPayloadEnvelope(sequence: Long): PraktikaBackupEnvelope {
    return envelope(notStartedPayload()).withSequence(sequence)
}

class FakeBackupStorage(
    var slotA: SlotReadResult = SlotReadResult.Missing,
    var slotB: SlotReadResult = SlotReadResult.Missing,
) : BackupStorageProvider {
    var inspectCount = 0

    override suspend fun inspectSlots(): SlotInspectionResult {
        inspectCount++
        return SlotInspectionResult(slotA = slotA, slotB = slotB)
    }

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult {
        return when (slot) {
            BackupSlotId.A -> slotA
            BackupSlotId.B -> slotB
        }
    }

    override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult = BackupSlotWriteResult.CreateFailed(slot)
}

class MutableBackupStorage(
    private var delegate: FakeBackupStorage,
) : BackupStorageProvider {
    override suspend fun inspectSlots(): SlotInspectionResult = delegate.inspectSlots()

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult = delegate.readSlot(slot)

    override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult = delegate.writeSlot(slot, bytes, expectedEnvelope)

    fun mutateSequence(sequence: Long) {
        val b = delegate.slotB as? SlotReadResult.Valid ?: return
        delegate = FakeBackupStorage(
            slotA = delegate.slotA,
            slotB = SlotReadResult.Valid(
                slot = BackupSlotId.B,
                envelope = b.envelope.withSequence(sequence),
            ),
        )
    }

    fun mutateChecksum(checksum: String) {
        val b = delegate.slotB as? SlotReadResult.Valid ?: return
        val env = b.envelope
        delegate = FakeBackupStorage(
            slotA = delegate.slotA,
            slotB = SlotReadResult.Valid(
                slot = BackupSlotId.B,
                envelope = PraktikaBackupEnvelope(
                    backupSchemaVersion = env.backupSchemaVersion,
                    backupSequence = env.backupSequence,
                    createdAtEpochMillis = env.createdAtEpochMillis,
                    sourceAppVersionCode = env.sourceAppVersionCode,
                    sourceAppVersionName = env.sourceAppVersionName,
                    sourceSeedVersion = env.sourceSeedVersion,
                    backupChecksumSha256 = checksum,
                    payload = env.payload,
                ),
            ),
        )
    }

    fun replaceWith(newStorage: FakeBackupStorage) {
        delegate = newStorage
    }
}

class RecordingRestoreEngine(
    private val delegate: BackupRestoreEngine? = null,
) : BackupRestoreEngine {
    var callCount = 0

    override suspend fun restore(envelope: PraktikaBackupEnvelope): BackupRestoreResult {
        callCount++
        return delegate?.restore(envelope) ?: BackupRestoreResult.Success
    }
}

private class DeviceRoundtripVerifier(
    private val exporter: RoomBackupExporter,
) : PostRestoreDataVerifier {
    override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
        return when (val exportResult = exporter.export()) {
            is com.me4hik.praktika.data.backup.export.BackupExportResult.Success -> {
                if (!BackupPayloadSemanticComparator.equalsSemantically(
                        restoredEnvelope.payload,
                        exportResult.payload,
                    )
                ) {
                    PostRestoreVerificationResult.Mismatch("payload_semantic_mismatch")
                } else {
                    PostRestoreVerificationResult.Success(
                        evidence = PostRestoreVerificationEvidence.fromPayload(exportResult.payload),
                    )
                }
            }

            else -> PostRestoreVerificationResult.Mismatch("export_failed")
        }
    }
}

private class RecordingPostRestoreVerifier(
    private val delegate: PostRestoreDataVerifier,
) : PostRestoreDataVerifier {
    var verifyCount = 0

    override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
        verifyCount++
        return delegate.verify(restoredEnvelope)
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
