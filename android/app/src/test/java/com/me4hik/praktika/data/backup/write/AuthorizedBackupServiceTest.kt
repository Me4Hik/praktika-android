package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedBackupServiceTest {
    private val folderA = "content://com.test/tree/folder-a"
    private val folderB = "content://com.test/tree/folder-b"
    private val trustedPhysicalCreatedAt = 1_700_000_300_000L
    private val clockCreatedAt = 1_800_000_000_000L
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload

    @Test
    fun noAuth_noCoordinator() = runTest {
        val harness = harness(auth = null, hint = folderB)
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(AuthorizedBackupResultKind.NotConfigured, result.kind)
        assertEquals(0, harness.resolver.resolveCount)
        assertEquals(0, harness.coordinatorCreateCount)
    }

    @Test
    fun malformedAuth_noCoordinator() = runTest {
        val harness = harness(auth = "   ", hint = folderB)
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(AuthorizedBackupResultKind.InvalidConfiguration, result.kind)
        assertEquals(0, harness.resolver.resolveCount)
        assertEquals(0, harness.coordinatorCreateCount)
    }

    @Test
    fun hintB_authA_resolvesOnlyA() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        harness.resolver.resultFor = mapOf(
            folderA to AuthorizedStorageResolveResult.Ready(emptyReadyStorage()),
            folderB to AuthorizedStorageResolveResult.Ready(emptyReadyStorage()),
        )
        harness.service.backupNow(BackupRequestReason.STARTUP_CATCHUP, BackupAttemptTrigger.STARTUP)
        assertEquals(listOf(folderA), harness.resolver.resolvedUris)
        assertFalse(harness.resolver.resolvedUris.contains(folderB))
        assertEquals(folderB, harness.repo.snapshot().treeUriHint)
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun grantOnB_doesNotSubstituteMissingAGrant() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        harness.resolver.resultFor = mapOf(
            folderA to AuthorizedStorageResolveResult.PermissionLost,
            folderB to AuthorizedStorageResolveResult.Ready(emptyReadyStorage()),
        )
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(AuthorizedBackupResultKind.NeedsReconnect, result.kind)
        assertEquals(0, harness.coordinatorCreateCount)
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
        assertTrue(harness.repo.snapshot().needsReconnect)
        assertEquals(BackupFailureStatus.PERMISSION_LOST, harness.repo.snapshot().lastFailureCategory)
    }

    @Test
    fun permissionStatusRace_AtoB_stale() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.resolver.onResolve = { uri ->
            if (uri == folderA) {
                harness.repo.setAuthorized(folderB)
            }
            AuthorizedStorageResolveResult.PermissionLost
        }
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(ConditionalStatusUpdateResult.StaleAttempt, result.statusUpdate)
        assertEquals(folderB, harness.repo.snapshot().authorizedTreeUri)
        assertFalse(harness.repo.snapshot().needsReconnect)
        assertNull(harness.repo.snapshot().lastFailureCategory)
    }

    @Test
    fun structuralFailure_mutationSuppressed() = runTest {
        val harness = harness(
            auth = folderA,
            hint = folderA,
            failure = BackupFailureStatus.AMBIGUOUS_SLOT,
        )
        val result = harness.service.backupNow(
            BackupRequestReason.ANSWER_SAVED,
            BackupAttemptTrigger.MUTATION,
        )
        assertEquals(AuthorizedBackupResultKind.RetrySuppressed, result.kind)
        assertEquals(0, harness.resolver.resolveCount)
        assertEquals(0, harness.coordinatorCreateCount)
    }

    @Test
    fun structuralFailure_startupManualRestoreAllowed() = runTest {
        for (trigger in listOf(
            BackupAttemptTrigger.STARTUP,
            BackupAttemptTrigger.MANUAL,
            BackupAttemptTrigger.RESTORE_CATCHUP,
        )) {
            val harness = harness(
                auth = folderA,
                hint = folderA,
                failure = BackupFailureStatus.AMBIGUOUS_SLOT,
            )
            val storage = RecordingBackupStorage(
                slotA = validRead(BackupSlotId.A, envelopeWithSequence(5L)),
            )
            storage.snapshotInspectionState()
            harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))
            val result = harness.service.backupNow(BackupRequestReason.STARTUP_CATCHUP, trigger)
            assertEquals(AuthorizedBackupResultKind.CoordinatorCompleted, result.kind)
            assertEquals(
                BackupOutcome.NoChange(trustedPhysicalCreatedAt),
                result.coordinatorOutcome,
            )
            assertEquals(ConditionalStatusUpdateResult.Applied, result.statusUpdate)
            assertNull(harness.repo.snapshot().lastFailureCategory)
            assertEquals(trustedPhysicalCreatedAt, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
            assertEquals(0, storage.writeCalls.size)
        }
    }

    @Test
    fun retryableFailure_mutationAllowed() = runTest {
        val harness = harness(
            auth = folderA,
            hint = folderA,
            failure = BackupFailureStatus.WRITE_FAILED,
        )
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))
        val result = harness.service.backupNow(
            BackupRequestReason.ANSWER_SAVED,
            BackupAttemptTrigger.MUTATION,
        )
        assertEquals(AuthorizedBackupResultKind.CoordinatorCompleted, result.kind)
        assertTrue(result.coordinatorOutcome is BackupOutcome.Written)
        assertEquals(1, storage.writeCalls.size)
    }

    @Test
    fun written_conditionalSuccessUsesVerifiedTimestamp() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        val written = result.coordinatorOutcome as BackupOutcome.Written
        assertEquals(clockCreatedAt, written.createdAtEpochMillis)
        assertEquals(clockCreatedAt, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertEquals(ConditionalStatusUpdateResult.Applied, result.statusUpdate)
    }

    @Test
    fun noChange_recoversTrustedTimestamp_zeroWrites() = runTest {
        val harness = harness(
            auth = folderA,
            hint = folderA,
            lastSuccess = null,
            failure = BackupFailureStatus.WRITE_FAILED,
        )
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(7L)),
        )
        storage.snapshotInspectionState()
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))
        val result = harness.service.backupNow(
            BackupRequestReason.RESTORE_CATCHUP,
            BackupAttemptTrigger.RESTORE_CATCHUP,
        )
        assertEquals(BackupOutcome.NoChange(trustedPhysicalCreatedAt), result.coordinatorOutcome)
        assertEquals(trustedPhysicalCreatedAt, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertNull(harness.repo.snapshot().lastFailureCategory)
        assertEquals(0, storage.writeCalls.size)
        assertFalse(result.statusPersistenceFailed)
    }

    @Test
    fun restoreSwitchRecoverySimulation() = runTest {
        val harness = harness(
            auth = folderB,
            hint = folderB,
            lastSuccess = null,
        )
        val storage = RecordingBackupStorage(
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(3L)),
        )
        storage.snapshotInspectionState()
        harness.resolver.resultFor = mapOf(folderB to AuthorizedStorageResolveResult.Ready(storage))
        val result = harness.service.backupNow(
            BackupRequestReason.RESTORE_CATCHUP,
            BackupAttemptTrigger.RESTORE_CATCHUP,
        )
        assertEquals(BackupOutcome.NoChange(trustedPhysicalCreatedAt), result.coordinatorOutcome)
        assertEquals(trustedPhysicalCreatedAt, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertEquals(0, storage.writeCalls.size)
    }

    @Test
    fun writtenAfterAuthSwitch_stale_Buntouched() = runTest {
        val harness = harness(auth = folderA, hint = folderA, lastSuccess = 111L)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        storage.writeDelayMillis = 50L
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        val deferred = async {
            harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        }
        delay(10L)
        harness.repo.replace(
            BackupWriteState(
                treeUriHint = folderB,
                authorizedTreeUri = folderB,
                lastSuccessfulBackupAtEpochMillis = 999L,
                lastFailureCategory = BackupFailureStatus.EXPORT_FAILED,
                needsReconnect = false,
            ),
        )
        val result = deferred.await()
        assertTrue(result.coordinatorOutcome is BackupOutcome.Written)
        assertEquals(ConditionalStatusUpdateResult.StaleAttempt, result.statusUpdate)
        assertEquals(folderB, harness.repo.snapshot().authorizedTreeUri)
        assertEquals(999L, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertEquals(BackupFailureStatus.EXPORT_FAILED, harness.repo.snapshot().lastFailureCategory)
    }

    @Test
    fun disableDuringAttempt_stale_noReauth() = runTest {
        val harness = harness(auth = folderA, hint = folderA, lastSuccess = 111L)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        storage.writeDelayMillis = 50L
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        val deferred = async {
            harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        }
        delay(10L)
        harness.repo.disableAutomaticBackup()
        val result = deferred.await()
        assertTrue(result.coordinatorOutcome is BackupOutcome.Written)
        assertEquals(ConditionalStatusUpdateResult.StaleAttempt, result.statusUpdate)
        assertNull(harness.repo.snapshot().authorizedTreeUri)
        assertNull(harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun physicalWritten_statusPersistFails_noSecondWrite() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))
        harness.repo.statusPersistShouldFail = true

        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(AuthorizedBackupResultKind.CoordinatorCompleted, result.kind)
        assertTrue(result.coordinatorOutcome is BackupOutcome.Written)
        assertTrue(result.statusPersistenceFailed)
        assertEquals(1, storage.writeCalls.size)
    }

    @Test
    fun needsReconnectPrecall_blocksProvider() = runTest {
        val harness = harness(auth = folderA, hint = folderA, needsReconnect = true)
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(AuthorizedBackupResultKind.NeedsReconnect, result.kind)
        assertEquals(0, harness.resolver.resolveCount)
    }

    @Test
    fun suppressionBlocksAttempt() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.service.suppressRestoreSession()
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(AuthorizedBackupResultKind.Suppressed, result.kind)
        assertEquals(0, harness.resolver.resolveCount)
        harness.service.releaseRestoreSession()
        assertFalse(harness.service.isRestoreSessionSuppressed())
    }

    @Test
    fun serviceSingleWriter() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        storage.writeDelayMillis = 40L
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        val first = async {
            harness.service.backupNow(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        }
        val second = async {
            delay(5L)
            harness.service.backupNow(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        }
        first.await()
        second.await()
        assertTrue(harness.maxConcurrentAttempts <= 1)
    }

    @Test
    fun requestCoalescing_collapsesToOneRerun() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val harness = harness(auth = folderA, hint = folderA, scope = scope, dispatcher = dispatcher)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        var exportPayload = goldenPayload
        harness.exportPayloadProvider = { BackupExportResult.Success(exportPayload) }
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        harness.service.requestBackup(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        harness.service.requestBackup(BackupRequestReason.ANSWER_DELETED, BackupAttemptTrigger.MUTATION)
        exportPayload = practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 9)
        harness.service.requestBackup(BackupRequestReason.SCHEDULE_CHANGED, BackupAttemptTrigger.MUTATION)
        scope.advanceUntilIdle()

        assertTrue(harness.coordinatorCreateCount <= 2)
        assertTrue(storage.writeCalls.size <= 2)
    }

    @Test
    fun authSwitchPendingRerun_usesB() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val harness = harness(auth = folderA, hint = folderA, scope = scope, dispatcher = dispatcher)
        val storageA = RecordingBackupStorage()
        storageA.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        storageA.writeDelayMillis = 30L
        val storageB = RecordingBackupStorage(
            slotB = validRead(BackupSlotId.B, envelopeWithSequence(4L)),
        )
        storageB.snapshotInspectionState()
        harness.resolver.onResolve = { uri ->
            when (uri) {
                folderA -> AuthorizedStorageResolveResult.Ready(storageA)
                folderB -> AuthorizedStorageResolveResult.Ready(storageB)
                else -> AuthorizedStorageResolveResult.Unavailable
            }
        }

        harness.service.requestBackup(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        dispatcher.scheduler.runCurrent()
        harness.repo.replace(
            BackupWriteState(
                treeUriHint = folderB,
                authorizedTreeUri = folderB,
                lastSuccessfulBackupAtEpochMillis = null,
                lastFailureCategory = null,
                needsReconnect = false,
            ),
        )
        harness.service.requestBackup(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        scope.advanceUntilIdle()

        assertTrue(harness.resolver.resolvedUris.contains(folderA))
        assertTrue(harness.resolver.resolvedUris.contains(folderB))
        assertEquals(folderB, harness.repo.snapshot().authorizedTreeUri)
        // B path is NoChange against existing valid B — zero writes on B
        assertEquals(0, storageB.writeCalls.size)
        assertEquals(1, storageA.writeCalls.size)
        // A's success must not land on B
        assertEquals(trustedPhysicalCreatedAt, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun disablePendingRerun_noSecondWrite() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val harness = harness(auth = folderA, hint = folderA, scope = scope, dispatcher = dispatcher)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        storage.writeDelayMillis = 30L
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        harness.service.requestBackup(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        dispatcher.scheduler.runCurrent()
        harness.repo.disableAutomaticBackup()
        harness.service.requestBackup(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        scope.advanceUntilIdle()

        assertEquals(1, storage.writeCalls.size)
        assertNull(harness.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun grantLossNeverClearsAuth() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.PermissionLost)
        harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
        assertTrue(harness.repo.snapshot().needsReconnect)
    }

    @Test
    fun outwardResult_hasNoRawUriFields() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        harness.resolver.resultFor = mapOf(
            folderA to AuthorizedStorageResolveResult.Ready(emptyReadyStorage()),
        )
        val result = harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        val text = result.toString()
        assertFalse(text.contains(folderA))
        assertFalse(text.contains(folderB))
    }

    @Test
    fun initializationActive_blocksMutationOnly() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(emptyReadyStorage()))
        harness.service.beginInitialization()

        val suppressed = harness.service.backupNow(
            BackupRequestReason.RUNTIME_RECONCILED,
            BackupAttemptTrigger.MUTATION,
        )
        assertEquals(AuthorizedBackupResultKind.SuppressedInitialization, suppressed.kind)
        assertEquals(0, harness.resolver.resolveCount)
        assertEquals(0, harness.coordinatorCreateCount)
        assertNull(harness.repo.snapshot().lastFailureCategory)

        val startupWhileInit = harness.service.backupNow(
            BackupRequestReason.STARTUP_CATCHUP,
            BackupAttemptTrigger.STARTUP,
        )
        assertEquals(AuthorizedBackupResultKind.CoordinatorCompleted, startupWhileInit.kind)
        assertEquals(1, harness.resolver.resolveCount)

        harness.service.endInitialization()
        val afterClear = harness.service.backupNow(
            BackupRequestReason.RUNTIME_RECONCILED,
            BackupAttemptTrigger.MUTATION,
        )
        assertEquals(AuthorizedBackupResultKind.CoordinatorCompleted, afterClear.kind)
        assertEquals(2, harness.resolver.resolveCount)
    }

    @Test
    fun sharedGate_defaultIsPrivateUnlessInjected() = runTest {
        val shared = BackupIoSessionGate()
        val harness = harness(auth = folderA, hint = folderA, ioGate = shared)
        assertTrue(harness.service.sharedIoGate === shared)
    }

    @Test
    fun supervisorScope_survivesFailedChildAttempt() = runTest {
        val supervisor = kotlinx.coroutines.SupervisorJob()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = kotlinx.coroutines.CoroutineScope(supervisor + dispatcher)
        val harness = harness(auth = folderA, hint = folderA, scope = scope, dispatcher = dispatcher)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        harness.exportPayloadProvider = { error("forced export failure") }
        harness.service.requestBackup(BackupRequestReason.STARTUP_CATCHUP, BackupAttemptTrigger.STARTUP)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(supervisor.isActive)

        harness.exportPayloadProvider = { BackupExportResult.Success(goldenPayload) }
        harness.service.requestBackup(BackupRequestReason.STARTUP_CATCHUP, BackupAttemptTrigger.STARTUP)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(supervisor.isActive)
        assertTrue(harness.coordinatorCreateCount >= 1)
    }

    @Test
    fun startup_authNull_hintOnly_notConfigured() = runTest {
        val harness = harness(auth = null, hint = folderB)
        val result = harness.service.backupNow(
            BackupRequestReason.STARTUP_CATCHUP,
            BackupAttemptTrigger.STARTUP,
        )
        assertEquals(AuthorizedBackupResultKind.NotConfigured, result.kind)
        assertEquals(0, harness.resolver.resolveCount)
        assertEquals(0, harness.coordinatorCreateCount)
    }

    @Test
    fun startup_grantLoss_preservesAuth() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.PermissionLost)
        val result = harness.service.backupNow(
            BackupRequestReason.STARTUP_CATCHUP,
            BackupAttemptTrigger.STARTUP,
        )
        assertEquals(AuthorizedBackupResultKind.NeedsReconnect, result.kind)
        assertEquals(0, harness.coordinatorCreateCount)
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
        assertTrue(harness.repo.snapshot().needsReconnect)
        assertEquals(BackupFailureStatus.PERMISSION_LOST, harness.repo.snapshot().lastFailureCategory)
    }

    private fun emptyReadyStorage(): BackupStorageProvider {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        return storage
    }

    private fun TestScope.harness(
        auth: String?,
        hint: String?,
        failure: BackupFailureStatus? = null,
        lastSuccess: Long? = null,
        needsReconnect: Boolean = false,
        scope: kotlinx.coroutines.CoroutineScope = this,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        ioGate: BackupIoSessionGate = BackupIoSessionGate(),
    ): ServiceHarness {
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = hint,
                authorizedTreeUri = auth,
                lastSuccessfulBackupAtEpochMillis = lastSuccess,
                lastFailureCategory = failure,
                needsReconnect = needsReconnect,
            ),
        )
        val resolver = FakeResolver()
        val attemptMutex = Mutex()
        var active = 0
        var maxActive = 0
        var createCount = 0
        var exportPayloadProvider: suspend () -> BackupExportResult = {
            BackupExportResult.Success(goldenPayload)
        }
        val factory = ProductionBackupCoordinatorFactory { storage ->
            createCount++
            val clock = CountingBackupClock(clockCreatedAt)
            ProductionBackupCoordinator(
                storage = storage,
                exportAction = {
                    attemptMutex.withLock {
                        active++
                        maxActive = maxOf(maxActive, active)
                    }
                    try {
                        exportPayloadProvider()
                    } finally {
                        attemptMutex.withLock { active-- }
                    }
                },
                metadataFactory = BackupSnapshotMetadataFactory(
                    clock = clock,
                    appMetadataProvider = FixedBackupAppMetadataProvider(),
                ),
                scope = scope,
                ioDispatcher = dispatcher,
            )
        }
        val service = AuthorizedBackupService(
            writeStateRepository = repo,
            storageResolver = resolver,
            coordinatorFactory = factory,
            scope = scope,
            ioDispatcher = dispatcher,
            ioGate = ioGate,
        )
        return ServiceHarness(
            service = service,
            repo = repo,
            resolver = resolver,
            getCoordinatorCreateCount = { createCount },
            getMaxConcurrentAttempts = { maxActive },
            setExportPayloadProvider = { exportPayloadProvider = it },
        )
    }

    private class FakeResolver : AuthorizedBackupStorageResolver {
        var resultFor: Map<String, AuthorizedStorageResolveResult> = emptyMap()
        var onResolve: (suspend (String) -> AuthorizedStorageResolveResult)? = null
        val resolvedUris = mutableListOf<String>()
        val resolveCount: Int get() = resolvedUris.size

        override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
            resolvedUris += authorizedUriString
            onResolve?.let { return it(authorizedUriString) }
            return resultFor[authorizedUriString] ?: AuthorizedStorageResolveResult.Unavailable
        }
    }

    private class ServiceHarness(
        val service: AuthorizedBackupService,
        val repo: InMemoryBackupWriteStateRepository,
        val resolver: FakeResolver,
        private val getCoordinatorCreateCount: () -> Int,
        private val getMaxConcurrentAttempts: () -> Int,
        private val setExportPayloadProvider: (suspend () -> BackupExportResult) -> Unit,
    ) {
        val coordinatorCreateCount: Int get() = getCoordinatorCreateCount()
        val maxConcurrentAttempts: Int get() = getMaxConcurrentAttempts()
        var exportPayloadProvider: suspend () -> BackupExportResult
            get() = error("write-only")
            set(value) = setExportPayloadProvider(value)
    }
}
