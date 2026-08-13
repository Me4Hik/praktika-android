package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 6.2B2-B: committed restore handoff ownership, auth finalize, RESTORE_CATCHUP exactly once.
 */
class Stage62B2BCommittedHandoffServiceTest {
    private val folderA = "content://auth/tree/A"
    private val folderB = "content://hint/tree/B"
    private val clockCreatedAt = 1_700_000_000_000L
    private val goldenPayload = BackupRestoreFixtures.richEnvelope().payload

    @Test
    fun beginCommitted_keepsSuppressionAndTransfersPhase() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        harness.service.beginRestoreSession()
        assertEquals("UI_ACTIVE", harness.service.restoreSessionPhaseForTests())
        val result = harness.service.beginCommittedRestoreHandoff()
        assertEquals(CommittedHandoffStartResult.Started, result)
        assertEquals("COMMITTED_HANDOFF", harness.service.restoreSessionPhaseForTests())
        assertTrue(harness.service.isRestoreSessionSuppressed())
    }

    @Test
    fun beginCommitted_duplicate_returnsAlreadyStarted() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        harness.service.beginRestoreSession()
        assertEquals(CommittedHandoffStartResult.Started, harness.service.beginCommittedRestoreHandoff())
        assertEquals(
            CommittedHandoffStartResult.AlreadyStarted,
            harness.service.beginCommittedRestoreHandoff(),
        )
    }

    @Test
    fun beginCommitted_inactive_returnsUnavailable() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        assertEquals(
            CommittedHandoffStartResult.Unavailable,
            harness.service.beginCommittedRestoreHandoff(),
        )
    }

    @Test
    fun beginWhileCommittedHandoff_doesNotStomp() = runTest {
        val harness = harness(auth = folderA, hint = folderB)
        harness.service.beginRestoreSession()
        harness.service.beginCommittedRestoreHandoff()
        harness.service.beginRestoreSession()
        assertEquals("COMMITTED_HANDOFF", harness.service.restoreSessionPhaseForTests())
        assertTrue(harness.service.isRestoreSessionSuppressed())
    }

    @Test
    fun authSuccess_releasesAndEnqueuesOneRestoreCatchup_subsumesDirty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(auth = folderA, hint = folderB, dispatcher = dispatcher)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(
            folderA to AuthorizedStorageResolveResult.Ready(storage),
            folderB to AuthorizedStorageResolveResult.Ready(storage),
        )

        harness.service.beginRestoreSession()
        harness.service.backupNow(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())

        assertEquals(CommittedHandoffStartResult.Started, harness.service.startCommittedHandoff())
        assertTrue(harness.service.isRestoreSessionSuppressed())
        advanceUntilIdle()

        assertFalse(harness.service.isRestoreSessionSuppressed())
        assertEquals("INACTIVE", harness.service.restoreSessionPhaseForTests())
        assertFalse(harness.service.isRestoreSuppressedWorkPendingForTests())
        assertEquals(1, requests.count { it.first == BackupRequestReason.RESTORE_CATCHUP })
        assertEquals(0, requests.count { it.first == BackupRequestReason.RUNTIME_RECONCILED })
        assertEquals(folderB, harness.repo.snapshot().authorizedTreeUri)
        assertTrue(harness.resolver.resolvedUris.all { it == folderB })
    }

    @Test
    fun authFailureDirty_oldAuthCatchup_noRestoreCatchup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(auth = folderA, hint = "not-a-usable-uri", dispatcher = dispatcher)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(storage))

        harness.service.beginRestoreSession()
        harness.service.backupNow(BackupRequestReason.STARTUP_CATCHUP, BackupAttemptTrigger.STARTUP)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())

        harness.service.startCommittedHandoff()
        advanceUntilIdle()

        assertFalse(harness.service.isRestoreSessionSuppressed())
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
        assertEquals(1, requests.count { it.first == BackupRequestReason.RUNTIME_RECONCILED })
        assertEquals(0, requests.count { it.first == BackupRequestReason.RESTORE_CATCHUP })
        assertTrue(harness.resolver.resolvedUris.all { it == folderA })
    }

    @Test
    fun authFailureClean_zeroCatchup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(auth = folderA, hint = "not-a-usable-uri", dispatcher = dispatcher)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        harness.service.beginRestoreSession()
        harness.service.startCommittedHandoff()
        advanceUntilIdle()
        assertFalse(harness.service.isRestoreSessionSuppressed())
        assertTrue(requests.isEmpty())
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun requestDuringCommittedHandoff_marksDirty_thenSubsumedByRestoreCatchup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(auth = folderA, hint = folderB, dispatcher = dispatcher)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(
            folderB to AuthorizedStorageResolveResult.Ready(storage),
        )

        harness.service.beginRestoreSession()
        harness.service.beginCommittedRestoreHandoff()
        assertTrue(harness.service.isRestoreSessionSuppressed())
        val mid = harness.service.backupNow(
            BackupRequestReason.ANSWER_SAVED,
            BackupAttemptTrigger.MUTATION,
        )
        assertEquals(AuthorizedBackupResultKind.Suppressed, mid.kind)
        assertTrue(harness.service.isRestoreSuppressedWorkPendingForTests())

        // Launch auth job without double-claiming.
        harness.service.startCommittedHandoff()
        advanceUntilIdle()

        assertEquals(1, requests.count { it.first == BackupRequestReason.RESTORE_CATCHUP })
        assertEquals(0, requests.count { it.first == BackupRequestReason.RUNTIME_RECONCILED })
    }

    @Test
    fun duplicateStartCommittedHandoff_oneJobOneCatchup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(auth = folderA, hint = folderB, dispatcher = dispatcher)
        val requests = mutableListOf<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        harness.service.onRequestBackupForTests = { reason, trigger ->
            requests += reason to trigger
        }
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        harness.resolver.resultFor = mapOf(folderB to AuthorizedStorageResolveResult.Ready(storage))

        harness.service.beginRestoreSession()
        assertEquals(CommittedHandoffStartResult.Started, harness.service.startCommittedHandoff())
        assertEquals(
            CommittedHandoffStartResult.AlreadyStarted,
            harness.service.startCommittedHandoff(),
        )
        advanceUntilIdle()
        assertEquals(1, requests.count { it.first == BackupRequestReason.RESTORE_CATCHUP })
    }

    @Test
    fun sameFolderAuth_preservesLastSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val lastSuccess = 1_650_000_000_000L
        val harness = harness(
            auth = folderA,
            hint = folderA,
            lastSuccess = lastSuccess,
            dispatcher = dispatcher,
        )
        harness.service.beginRestoreSession()
        harness.service.startCommittedHandoff()
        advanceUntilIdle()
        assertEquals(folderA, harness.repo.snapshot().authorizedTreeUri)
        assertEquals(lastSuccess, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun aToB_clearsLastSuccessOnAuth() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(
            auth = folderA,
            hint = folderB,
            lastSuccess = 1_650_000_000_000L,
            dispatcher = dispatcher,
        )
        harness.service.beginRestoreSession()
        harness.service.startCommittedHandoff()
        advanceUntilIdle()
        assertEquals(folderB, harness.repo.snapshot().authorizedTreeUri)
        assertEquals(null, harness.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun nullToB_authorizes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val harness = harness(auth = null, hint = folderB, dispatcher = dispatcher)
        harness.service.beginRestoreSession()
        harness.service.startCommittedHandoff()
        advanceUntilIdle()
        assertEquals(folderB, harness.repo.snapshot().authorizedTreeUri)
        assertFalse(harness.service.isRestoreSessionSuppressed())
    }

    private fun TestScope.harness(
        auth: String?,
        hint: String?,
        lastSuccess: Long? = null,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        ioGate: BackupIoSessionGate = BackupIoSessionGate(),
        scope: kotlinx.coroutines.CoroutineScope = this,
    ): ServiceHarness {
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = hint,
                authorizedTreeUri = auth,
                lastSuccessfulBackupAtEpochMillis = lastSuccess,
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
        return ServiceHarness(service, repo, resolver)
    }

    private class FakeResolver : AuthorizedBackupStorageResolver {
        var resultFor: Map<String, AuthorizedStorageResolveResult> = emptyMap()
        val resolvedUris = mutableListOf<String>()

        override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
            resolvedUris += authorizedUriString
            return resultFor[authorizedUriString] ?: AuthorizedStorageResolveResult.Unavailable
        }
    }

    private class ServiceHarness(
        val service: AuthorizedBackupService,
        val repo: InMemoryBackupWriteStateRepository,
        val resolver: FakeResolver,
    )
}
