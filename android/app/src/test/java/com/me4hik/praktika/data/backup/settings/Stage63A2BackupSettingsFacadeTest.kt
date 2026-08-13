package com.me4hik.praktika.data.backup.settings

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator
import com.me4hik.praktika.data.backup.setup.SetupCandidateAccess
import com.me4hik.praktika.data.backup.setup.SetupCandidateClassification
import com.me4hik.praktika.data.backup.setup.SetupCandidateConnectResult
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.write.AuthorizedBackupResult
import com.me4hik.praktika.data.backup.write.AuthorizedBackupResultKind
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.AuthorizedBackupStorageResolver
import com.me4hik.praktika.data.backup.write.AuthorizedStorageResolveResult
import com.me4hik.praktika.data.backup.write.BackupAttemptTrigger
import com.me4hik.praktika.data.backup.write.BackupFailureStatus
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupOutcome
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.backup.write.BackupWriteState
import com.me4hik.praktika.data.backup.write.CountingBackupClock
import com.me4hik.praktika.data.backup.write.FixedBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.write.InMemoryBackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinator
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinatorFactory
import com.me4hik.praktika.data.backup.write.RecordingBackupStorage
import com.me4hik.praktika.data.backup.write.envelopeWithSequence
import com.me4hik.praktika.data.backup.write.validRead
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage63A2BackupSettingsFacadeTest {
    private val folderA = "content://com.test/tree/folder-a"
    private val folderB = "content://com.test/tree/folder-b"
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload
    private val clockEpoch = 1_800_000_000_000L

    @Test
    fun statusFlow_mapsAuthNullToNotConfigured() = runTest {
        val h = harness(auth = null, needsReconnect = true, lastSuccess = 9L)
        assertEquals(
            BackupSettingsOperationalState.NotConfigured,
            h.facade.observeOperationalStatus().first(),
        )
    }

    @Test
    fun setupFacade_hidesTokenAndReceipt() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(auth = null, candidateUri = folderB, candidateStorage = storage)

        val inspect = h.facade.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        val ready = inspect as BackupSettingsSetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.EMPTY, ready.classification)
        assertTrue(h.facade.hasRetainedInspectionTokenForTests())
        assertFalse(ready.toString().contains("token"))
        assertFalse(ready.toString().contains(folderB))

        val verified = h.facade.verifyCurrentCandidate()
        assertEquals(BackupSettingsSetupVerifyResult.Verified, verified)
        assertFalse(verified.toString().contains("receipt"))
        assertFalse(verified.toString().contains("checksum"))

        val commit = h.facade.commitVerifiedCandidate() as BackupSettingsSetupCommitResult.Success
        assertEquals(clockEpoch, commit.verifiedCreatedAtEpochMillis)
        assertFalse(commit.toString().contains(folderB))
        assertFalse(h.facade.hasRetainedInspectionTokenForTests())
    }

    @Test
    fun candidateStale_updatesInternalToken_returnsClassificationOnly() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(auth = null, candidateUri = folderB, candidateStorage = storage)
        val ready = h.facade.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as BackupSettingsSetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.EMPTY, ready.classification)

        // Corrupt folder after inspect so verify reclassifies to blocked/stale path.
        storage.replaceSlotForTest(
            BackupSlotId.A,
            com.me4hik.praktika.data.backup.write.invalidRead(BackupSlotId.A),
        )
        storage.replaceSlotForTest(
            BackupSlotId.B,
            com.me4hik.praktika.data.backup.write.invalidRead(BackupSlotId.B),
        )
        val verify = h.facade.verifyCurrentCandidate()
        assertTrue(
            verify is BackupSettingsSetupVerifyResult.CandidateStale ||
                verify is BackupSettingsSetupVerifyResult.Blocked,
        )
        when (verify) {
            is BackupSettingsSetupVerifyResult.CandidateStale -> {
                assertEquals(SetupCandidateClassification.BOTH_UNTRUSTED, verify.classification)
                assertTrue(h.facade.hasRetainedInspectionTokenForTests())
            }
            is BackupSettingsSetupVerifyResult.Blocked ->
                assertEquals(SetupCandidateClassification.BOTH_UNTRUSTED, verify.classification)
            else -> error("unexpected $verify")
        }
        assertFalse(verify.toString().contains("token="))
    }

    @Test
    fun reconnect_differentFolder_noTakeNoRelease() = runTest {
        val reconnect = FakeReconnectAccess()
        val h = harness(
            auth = folderA,
            needsReconnect = true,
            reconnectAccess = reconnect,
        )
        val result = h.facade.reconnectFolderAccess(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertEquals(BackupReconnectResult.DifferentFolder, result)
        assertEquals(0, reconnect.takeCount.get())
        assertEquals(0, reconnect.releaseCount.get())
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
        assertEquals(0, h.catchupRequests.size)
    }

    @Test
    fun reconnect_success_markAndCatchupOnce() = runTest {
        val reconnect = FakeReconnectAccess(hasGrantInitially = false)
        reconnect.acquireResult = BackupReconnectAcquireResult.AcquiredAndValid
        val h = harness(
            auth = folderA,
            needsReconnect = true,
            lastSuccess = 5L,
            reconnectAccess = reconnect,
        )
        val result = h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertEquals(BackupReconnectResult.Success, result)
        assertEquals(1, reconnect.takeCount.get())
        assertEquals(0, reconnect.releaseCount.get())
        assertFalse(h.repo.snapshot().needsReconnect)
        assertEquals(1, h.catchupRequests.size)
        assertEquals(BackupRequestReason.MANUAL, h.catchupRequests.single().first)
        assertEquals(BackupAttemptTrigger.MANUAL, h.catchupRequests.single().second)
        assertTrue(reconnect.hasPersistedGrant(folderA))
    }

    @Test
    fun reconnect_notRequired_and_notConfigured() = runTest {
        val reconnect = FakeReconnectAccess()
        val healthy = harness(auth = folderA, needsReconnect = false, reconnectAccess = reconnect)
        assertEquals(
            BackupReconnectResult.NotReconnectRequired,
            healthy.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS),
        )
        assertEquals(0, reconnect.takeCount.get())

        val none = harness(auth = null, reconnectAccess = reconnect)
        assertEquals(
            BackupReconnectResult.NotConfigured,
            none.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS),
        )
        assertEquals(0, reconnect.takeCount.get())
    }

    @Test
    fun reconnect_permissionFailure_noCleanup() = runTest {
        val reconnect = FakeReconnectAccess()
        reconnect.acquireResult = BackupReconnectAcquireResult.PermissionAcquireFailed
        val h = harness(auth = folderA, needsReconnect = true, reconnectAccess = reconnect)
        assertEquals(
            BackupReconnectResult.PermissionFailure,
            h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS),
        )
        assertEquals(0, reconnect.releaseCount.get())
        assertTrue(h.repo.snapshot().needsReconnect)
        assertEquals(0, h.catchupRequests.size)
    }

    @Test
    fun reconnect_validationFail_authA_keepsGrant() = runTest {
        val reconnect = FakeReconnectAccess(hasGrantInitially = false)
        reconnect.acquireResult = BackupReconnectAcquireResult.ValidationFailed
        reconnect.grantOnAcquire = true
        val h = harness(auth = folderA, needsReconnect = true, reconnectAccess = reconnect)
        assertEquals(
            BackupReconnectResult.ValidationFailure,
            h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS),
        )
        assertEquals(0, reconnect.releaseCount.get())
        assertTrue(reconnect.hasPersistedGrant(folderA))
        assertTrue(h.repo.snapshot().needsReconnect)
        assertEquals(0, h.catchupRequests.size)
    }

    @Test
    fun reconnect_staleAuthAtoB_keepsGrant_evenIfNewlyReacquired() = runTest {
        val reconnect = FakeReconnectAccess(hasGrantInitially = false)
        reconnect.acquireResult = BackupReconnectAcquireResult.AcquiredAndValid
        reconnect.grantOnAcquire = true
        val h = harness(auth = folderA, needsReconnect = true, reconnectAccess = reconnect)
        reconnect.onAfterAcquire = {
            h.repo.setAuthorized(folderB)
        }
        val result = h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertEquals(BackupReconnectResult.StaleConfiguration, result)
        assertEquals(0, reconnect.releaseCount.get())
        assertTrue(reconnect.hasPersistedGrant(folderA))
        assertEquals(0, h.catchupRequests.size)
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun reconnect_staleAuthNull_releasesUnderGate() = runTest {
        val reconnect = FakeReconnectAccess(hasGrantInitially = false)
        reconnect.acquireResult = BackupReconnectAcquireResult.AcquiredAndValid
        reconnect.grantOnAcquire = true
        val h = harness(auth = folderA, needsReconnect = true, reconnectAccess = reconnect)
        reconnect.onAfterAcquire = {
            h.repo.disableAutomaticBackup()
        }
        val result = h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertEquals(BackupReconnectResult.StaleConfiguration, result)
        assertEquals(1, reconnect.releaseCount.get())
        assertFalse(reconnect.hasPersistedGrant(folderA))
        assertEquals(0, h.catchupRequests.size)
    }

    @Test
    fun reconnect_nullRecheckBecomesA_doesNotRelease() = runTest {
        val reconnect = FakeReconnectAccess(hasGrantInitially = false)
        reconnect.acquireResult = BackupReconnectAcquireResult.ValidationFailed
        reconnect.grantOnAcquire = true
        val gate = BackupIoSessionGate()
        val held = CompletableDeferred<Unit>()
        val releaseHold = CompletableDeferred<Unit>()
        val h = harness(
            auth = folderA,
            needsReconnect = true,
            reconnectAccess = reconnect,
            sharedGate = gate,
        )
        // Hold gate so cleanup waits; meanwhile restore auth A after temporary clear.
        val holder = launch {
            gate.withExclusive {
                held.complete(Unit)
                releaseHold.await()
            }
        }
        held.await()
        reconnect.onAfterAcquire = {
            h.repo.disableAutomaticBackup()
        }
        val job = async {
            h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        }
        yield()
        // While cleanup waits on gate, auth becomes A again.
        h.repo.setAuthorized(folderA)
        h.repo.markNeedsReconnect()
        releaseHold.complete(Unit)
        holder.join()
        assertEquals(BackupReconnectResult.ValidationFailure, job.await())
        assertEquals(0, reconnect.releaseCount.get())
        assertTrue(reconnect.hasPersistedGrant(folderA))
    }

    @Test
    fun reconnect_validationFail_authNull_releases() = runTest {
        val reconnect = FakeReconnectAccess(hasGrantInitially = false)
        reconnect.acquireResult = BackupReconnectAcquireResult.ValidationFailed
        reconnect.grantOnAcquire = true
        val h = harness(auth = folderA, needsReconnect = true, reconnectAccess = reconnect)
        reconnect.onAfterAcquire = { h.repo.disableAutomaticBackup() }
        assertEquals(
            BackupReconnectResult.ValidationFailure,
            h.facade.reconnectFolderAccess(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS),
        )
        assertEquals(1, reconnect.releaseCount.get())
        assertFalse(reconnect.hasPersistedGrant(folderA))
    }

    @Test
    fun backupNow_written_noChange_statusIncomplete_andFailures() = runTest {
        val writtenStorage = RecordingBackupStorage()
        writtenStorage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val written = harness(auth = folderA, candidateStorage = writtenStorage)
        written.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(writtenStorage))
        val writtenResult = written.facade.runBackupNow()
        assertEquals(BackupNowResult.Written, writtenResult)
        assertFalse(writtenResult.toString().contains("slot"))
        assertFalse(writtenResult.toString().contains("sequence"))

        val equal = envelopeWithSequence(5L)
        val noChangeStorage = RecordingBackupStorage(slotB = validRead(BackupSlotId.B, equal))
        val noChange = harness(auth = folderA, candidateStorage = noChangeStorage)
        noChange.resolver.resultFor =
            mapOf(folderA to AuthorizedStorageResolveResult.Ready(noChangeStorage))
        assertEquals(BackupNowResult.NoChange, noChange.facade.runBackupNow())

        val incomplete = AuthorizedBackupResult(
            kind = AuthorizedBackupResultKind.CoordinatorCompleted,
            coordinatorOutcome = BackupOutcome.Written(BackupSlotId.A, 1L, 9L),
            statusPersistenceFailed = true,
        )
        assertEquals(
            BackupNowResult.StatusTrackingIncomplete(BackupNowPhysicalResult.WRITTEN),
            BackupNowResultMapper.map(incomplete),
        )
        assertEquals(
            BackupNowResult.StatusTrackingIncomplete(BackupNowPhysicalResult.NO_CHANGE),
            BackupNowResultMapper.map(
                AuthorizedBackupResult(
                    kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                    coordinatorOutcome = BackupOutcome.NoChange(9L),
                    statusPersistenceFailed = true,
                ),
            ),
        )

        assertEquals(
            BackupNowResult.NotConfigured,
            BackupNowResultMapper.map(AuthorizedBackupResult(AuthorizedBackupResultKind.NotConfigured)),
        )
        assertEquals(
            BackupNowResult.NeedsReconnect,
            BackupNowResultMapper.map(AuthorizedBackupResult(AuthorizedBackupResultKind.NeedsReconnect)),
        )
        assertEquals(
            BackupNowResult.TemporarilyUnavailable,
            BackupNowResultMapper.map(AuthorizedBackupResult(AuthorizedBackupResultKind.Suppressed)),
        )
        assertEquals(
            BackupNowResult.NeedsAttention,
            BackupNowResultMapper.map(
                AuthorizedBackupResult(
                    kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                    coordinatorOutcome = BackupOutcome.AmbiguousSlot,
                ),
            ),
        )
        assertEquals(
            BackupNowResult.DataProblem,
            BackupNowResultMapper.map(
                AuthorizedBackupResult(
                    kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                    coordinatorOutcome = BackupOutcome.UnsafeDatabaseState,
                ),
            ),
        )
        assertEquals(
            BackupNowResult.TransientFailure,
            BackupNowResultMapper.map(
                AuthorizedBackupResult(
                    kind = AuthorizedBackupResultKind.CoordinatorCompleted,
                    coordinatorOutcome = BackupOutcome.StorageReadFailed,
                ),
            ),
        )
    }

    @Test
    fun disable_mapping_passthrough() = runTest {
        val h = harness(auth = folderA)
        assertEquals(BackupSettingsDisableResult.Success, h.facade.disableAutomaticBackup())
        assertNull(h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun runtimeAbandon_duringVerify_waitsThenReleasesUnbound() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val blockWrite = CompletableDeferred<Unit>()
        val writeStarted = CompletableDeferred<Unit>()
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        storage.onWriteSuspend = {
            writeStarted.complete(Unit)
            blockWrite.await()
        }
        val runtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val h = harness(
            auth = null,
            candidateUri = folderB,
            candidateStorage = storage,
            backupScope = runtimeScope,
            dispatcher = dispatcher,
        )
        val ready = h.facade.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertTrue(ready is BackupSettingsSetupInspectResult.Ready)
        assertTrue(h.access.hasPersistedGrant(folderB))

        val verifyJob = async(dispatcher) { h.facade.verifyCurrentCandidate() }
        writeStarted.await()
        h.facade.requestAbandonSetupSession()
        advanceUntilIdle()
        // Still mid-write: grant must remain.
        assertTrue(h.access.hasPersistedGrant(folderB))
        blockWrite.complete(Unit)
        assertEquals(BackupSettingsSetupVerifyResult.Verified, verifyJob.await())
        advanceUntilIdle()
        assertFalse(h.access.hasPersistedGrant(folderB))
        assertEquals(1, h.access.releaseCount)
        runtimeScope.cancel()
    }

    @Test
    fun runtimeAbandon_callerCancel_cleanupContinues() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(
            auth = null,
            candidateUri = folderB,
            candidateStorage = storage,
            backupScope = runtimeScope,
            dispatcher = dispatcher,
        )
        h.facade.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertTrue(h.access.hasPersistedGrant(folderB))
        val caller = CoroutineScope(SupervisorJob() + dispatcher)
        caller.launch {
            h.facade.requestAbandonSetupSession()
            // Non-suspending request returns; caller may die immediately after.
        }
        advanceUntilIdle() // schedule request onto runtimeScope
        caller.cancel()
        advanceUntilIdle() // runtime cleanup continues on backupCoroutineScope
        assertFalse(h.access.hasPersistedGrant(folderB))
        runtimeScope.cancel()
    }

    @Test
    fun runtimeAbandon_commitWins_preservesAuthorizedGrant() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val h = harness(
            auth = null,
            candidateUri = folderB,
            candidateStorage = storage,
            backupScope = runtimeScope,
        )
        h.facade.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertEquals(BackupSettingsSetupVerifyResult.Verified, h.facade.verifyCurrentCandidate())
        assertEquals(
            BackupSettingsSetupCommitResult.Success(clockEpoch, true).verifiedCreatedAtEpochMillis,
            (h.facade.commitVerifiedCandidate() as BackupSettingsSetupCommitResult.Success)
                .verifiedCreatedAtEpochMillis,
        )
        assertTrue(h.access.hasPersistedGrant(folderB))
        h.facade.requestAbandonSetupSession()
        assertTrue(h.access.hasPersistedGrant(folderB))
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        runtimeScope.cancel()
    }

    @Test
    fun runtimeAbandon_duplicate_releaseAtMostOnce() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val h = harness(
            auth = null,
            candidateUri = folderB,
            candidateStorage = storage,
            backupScope = runtimeScope,
        )
        h.facade.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        h.facade.requestAbandonSetupSession()
        h.facade.requestAbandonSetupSession()
        h.facade.requestAbandonSetupSession()
        assertFalse(h.access.hasPersistedGrant(folderB))
        assertTrue(h.access.releaseCount <= 1)
        assertNull(h.repo.snapshot().authorizedTreeUri)
        runtimeScope.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.harness(
        auth: String? = folderA,
        needsReconnect: Boolean = false,
        lastSuccess: Long? = if (auth != null) 1L else null,
        failure: BackupFailureStatus? = null,
        candidateUri: String = folderB,
        candidateStorage: RecordingBackupStorage = RecordingBackupStorage(),
        reconnectAccess: FakeReconnectAccess = FakeReconnectAccess(),
        sharedGate: BackupIoSessionGate = BackupIoSessionGate(),
        backupScope: CoroutineScope = this,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
    ): Harness {
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = auth,
                authorizedTreeUri = auth,
                lastSuccessfulBackupAtEpochMillis = lastSuccess,
                lastFailureCategory = failure,
                needsReconnect = needsReconnect,
            ),
        )
        val access = FakeCandidateAccess(mapOf(candidateUri to candidateStorage))
        if (auth != null) {
            access.seedGrant(auth)
        }
        val catchupRequests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val resolver = RecordingResolver()
        resolver.resultFor = mapOf(
            candidateUri to AuthorizedStorageResolveResult.Ready(candidateStorage),
        )
        if (auth != null) {
            resolver.resultFor = resolver.resultFor + mapOf(
                auth to AuthorizedStorageResolveResult.Ready(candidateStorage),
            )
        }
        val service = AuthorizedBackupService(
            writeStateRepository = repo,
            storageResolver = resolver,
            coordinatorFactory = ProductionBackupCoordinatorFactory { storage ->
                ProductionBackupCoordinator(
                    storage = storage,
                    exportAction = { BackupExportResult.Success(goldenPayload) },
                    metadataFactory = BackupSnapshotMetadataFactory(
                        clock = CountingBackupClock(clockEpoch),
                        appMetadataProvider = FixedBackupAppMetadataProvider(),
                    ),
                    scope = this,
                    ioDispatcher = dispatcher,
                )
            },
            scope = this,
            ioDispatcher = dispatcher,
            ioGate = sharedGate,
        )
        service.onRequestBackupForTests = { reason, trigger ->
            catchupRequests += reason to trigger
        }
        val coordinator = BackupFolderSetupCoordinator(
            writeStateRepository = repo,
            ioGate = sharedGate,
            authorizedBackupService = service,
            candidateAccess = access,
            exportAction = { BackupExportResult.Success(goldenPayload) },
            metadataFactory = BackupSnapshotMetadataFactory(
                clock = CountingBackupClock(clockEpoch),
                appMetadataProvider = FixedBackupAppMetadataProvider(),
            ),
        )
        val facade = BackupSettingsFacade(
            writeStateRepository = repo,
            authorizedBackupService = service,
            setupCoordinator = coordinator,
            ioGate = sharedGate,
            reconnectAccess = reconnectAccess,
            backupScope = backupScope,
        )
        return Harness(facade, service, repo, access, catchupRequests, resolver, reconnectAccess)
    }

    private class Harness(
        val facade: BackupSettingsFacade,
        val service: AuthorizedBackupService,
        val repo: InMemoryBackupWriteStateRepository,
        val access: FakeCandidateAccess,
        val catchupRequests: CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>,
        val resolver: RecordingResolver,
        val reconnectAccess: FakeReconnectAccess,
    )

    private class FakeReconnectAccess(
        hasGrantInitially: Boolean = false,
    ) : BackupReconnectAccess {
        val takeCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)
        private val grants = mutableSetOf<String>()
        var acquireResult: BackupReconnectAcquireResult = BackupReconnectAcquireResult.AcquiredAndValid
        var grantOnAcquire: Boolean = true
        var onAfterAcquire: (suspend () -> Unit)? = null

        init {
            if (hasGrantInitially) {
                // optional seed unused in most tests
            }
        }

        override suspend fun acquireAndValidate(
            uriString: String,
            grantFlags: Int,
        ): BackupReconnectAcquireResult {
            takeCount.incrementAndGet()
            if (grantOnAcquire &&
                acquireResult != BackupReconnectAcquireResult.PermissionAcquireFailed
            ) {
                grants += uriString
            }
            onAfterAcquire?.invoke()
            return acquireResult
        }

        override fun releasePersistedGrant(uriString: String, grantFlags: Int) {
            releaseCount.incrementAndGet()
            grants -= uriString
        }

        override fun hasPersistedGrant(uriString: String): Boolean = uriString in grants
    }

    private class FakeCandidateAccess(
        private val storageByUri: Map<String, BackupStorageProvider>,
    ) : SetupCandidateAccess {
        private val grants = mutableSetOf<String>()
        val released = mutableListOf<String>()
        val releaseCount: Int get() = released.size

        fun seedGrant(uri: String) {
            grants += uri
        }

        override suspend fun connectTransient(
            uriString: String,
            grantFlags: Int,
        ): SetupCandidateConnectResult {
            grants += uriString
            return SetupCandidateConnectResult.Success
        }

        override fun createStorage(uriString: String): BackupStorageProvider? = storageByUri[uriString]

        override fun releasePersistedGrant(uriString: String, grantFlags: Int) {
            released += uriString
            grants -= uriString
        }

        override fun hasPersistedGrant(uriString: String): Boolean = uriString in grants
    }

    private class RecordingResolver : AuthorizedBackupStorageResolver {
        var resultFor: Map<String, AuthorizedStorageResolveResult> = emptyMap()
        override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
            return resultFor[authorizedUriString] ?: AuthorizedStorageResolveResult.Unavailable
        }
    }
}


