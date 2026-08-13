package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 6.2B2-A: restore session API, dirty tracking, pending-rerun fairness.
 */
class Stage62B2ARestoreSessionServiceTest {
    private val folderA = "content://auth/tree/A"
    private val folderB = "content://hint/tree/B"
    private val clockCreatedAt = 1_700_000_000_000L
    private val goldenPayload = BackupRestoreFixtures.richEnvelope().payload

    @Test
    fun suppressedRequest_marksDirtyBoolean() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.service.beginRestoreSession()
        val result = harness.service.backupNow(
            BackupRequestReason.STARTUP_CATCHUP,
            BackupAttemptTrigger.STARTUP,
        )
        assertEquals(AuthorizedBackupResultKind.Suppressed, result.kind)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())
        assertEquals(0, harness.resolver.resolveCount)
    }

    @Test
    fun dirtyCancel_enqueuesOneRuntimeReconciledStartupCatchup_targetingAuth() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        harness.service.beginRestoreSession()
        harness.service.backupNow(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())

        harness.service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)

        assertFalse(harness.service.isRestoreSessionSuppressed())
        assertFalse(harness.service.isRestoreSessionActive())
        assertFalse(harness.service.isRestoreSuppressedWorkPendingForTests())
        assertEquals(1, requests.count { it.first == BackupRequestReason.RUNTIME_RECONCILED })
        assertEquals(BackupAttemptTrigger.STARTUP, requests.first().second)

        advanceUntilIdle()
        assertTrue(harness.resolver.resolvedUris.isNotEmpty())
        assertTrue(harness.resolver.resolvedUris.all { it == folderA })
        assertFalse(harness.resolver.resolvedUris.contains(folderB))
    }

    @Test
    fun cleanCancel_zeroCatchup() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        harness.service.beginRestoreSession()
        harness.service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
        assertTrue(requests.isEmpty())
        assertFalse(harness.service.isRestoreSessionSuppressed())
    }

    @Test
    fun committedDeferredHandoff_clearsDirty_noCatchup() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        harness.service.beginRestoreSession()
        harness.service.backupNow(BackupRequestReason.STARTUP_CATCHUP, BackupAttemptTrigger.STARTUP)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())

        harness.service.endRestoreSession(RestoreSessionEndReason.COMMITTED_DEFERRED_HANDOFF)

        assertFalse(harness.service.isRestoreSessionSuppressed())
        assertFalse(harness.service.isRestoreSuppressedWorkPendingForTests())
        assertTrue(requests.none { it.first == BackupRequestReason.RUNTIME_RECONCILED })
        assertTrue(requests.none { it.first == BackupRequestReason.RESTORE_CATCHUP })
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
        assertEquals(folderB, harness.repo.snapshot().treeUriHint)
    }

    @Test
    fun sessionEnd_idempotent_noDuplicateCatchup() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        harness.service.beginRestoreSession()
        harness.service.beginRestoreSession()
        harness.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        harness.service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
        harness.service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
        assertEquals(1, requests.count { it.first == BackupRequestReason.RUNTIME_RECONCILED })
    }

    @Test
    fun initializationAndRestoreFlags_independent() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        harness.service.beginInitialization()
        harness.service.beginRestoreSession()
        assertTrue(harness.service.isInitializationActive())
        assertTrue(harness.service.isRestoreSessionSuppressed())

        harness.service.endRestoreSession(RestoreSessionEndReason.CANCELLED_OR_FAILED)
        assertTrue(harness.service.isInitializationActive())
        assertFalse(harness.service.isRestoreSessionSuppressed())

        harness.service.beginRestoreSession()
        harness.service.endInitialization()
        assertFalse(harness.service.isInitializationActive())
        assertTrue(harness.service.isRestoreSessionSuppressed())
    }

    @Test
    fun pendingRerun_observesSuppression_marksDirty_skipsGate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = BackupIoSessionGate()
        val harness = harness(
            auth = folderA,
            hint = folderA,
            dispatcher = dispatcher,
            ioGate = gate,
            scope = this,
        )
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        val enteredPhysical = CompletableDeferred<Unit>()
        val releasePhysical = CompletableDeferred<Unit>()
        storage.onWriteSuspend = {
            enteredPhysical.complete(Unit)
            releasePhysical.await()
        }
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        harness.service.requestBackup(
            BackupRequestReason.ANSWER_SAVED,
            BackupAttemptTrigger.MUTATION,
        )
        dispatcher.scheduler.runCurrent()
        enteredPhysical.await()

        // Coalesce while first attempt holds gate; requestLoopActive prevents second loop.
        harness.service.requestBackup(
            BackupRequestReason.ANSWER_SAVED,
            BackupAttemptTrigger.MUTATION,
        )
        harness.service.beginRestoreSession()
        val entersWhileHeld = gate.enterCountForTests

        releasePhysical.complete(Unit)
        advanceUntilIdle()

        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())
        assertEquals(1, gate.maxExclusiveDepthForTests)
        // Suppressed pending rerun must not acquire the shared gate again.
        assertEquals(entersWhileHeld, gate.enterCountForTests)
        assertEquals(1, harness.resolver.resolveCount)
    }

    @Test
    fun afterEndRestore_futureAttemptsAccepted() = runTest {
        val harness = harness(auth = folderA, hint = folderA)
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))
        harness.service.beginRestoreSession()
        harness.service.endRestoreSession(RestoreSessionEndReason.COMMITTED_DEFERRED_HANDOFF)
        val result = harness.service.backupNow(
            BackupRequestReason.STARTUP_CATCHUP,
            BackupAttemptTrigger.STARTUP,
        )
        assertEquals(AuthorizedBackupResultKind.CoordinatorCompleted, result.kind)
        assertTrue(harness.resolver.resolveCount >= 1)
    }

    private fun TestScope.harness(
        auth: String?,
        hint: String?,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        ioGate: BackupIoSessionGate = BackupIoSessionGate(),
        scope: kotlinx.coroutines.CoroutineScope = this,
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
        val factory = ProductionBackupCoordinatorFactory { storage ->
            ProductionBackupCoordinator(
                storage = storage,
                exportAction = { BackupExportResult.Success(goldenPayload) },
                metadataFactory = BackupSnapshotMetadataFactory(
                    clock = CountingBackupClock(clockCreatedAt),
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
        return ServiceHarness(service, repo, resolver, ioGate)
    }

    private class FakeResolver : AuthorizedBackupStorageResolver {
        var resultFor: Map<String, AuthorizedStorageResolveResult> = emptyMap()
        val resolvedUris = mutableListOf<String>()
        val resolveCount: Int get() = resolvedUris.size

        override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
            resolvedUris += authorizedUriString
            return resultFor[authorizedUriString] ?: AuthorizedStorageResolveResult.Unavailable
        }
    }

    private class ServiceHarness(
        val service: AuthorizedBackupService,
        val repo: InMemoryBackupWriteStateRepository,
        val resolver: FakeResolver,
        val gate: BackupIoSessionGate,
    )
}
