package com.me4hik.praktika.data.backup.setup

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.AuthorizedBackupStorageResolver
import com.me4hik.praktika.data.backup.write.AuthorizedStorageResolveResult
import com.me4hik.praktika.data.backup.write.BackupAttemptTrigger
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.backup.write.BackupWriteState
import com.me4hik.praktika.data.backup.write.ConditionalStatusUpdateResult
import com.me4hik.praktika.data.backup.write.CountingBackupClock
import com.me4hik.praktika.data.backup.write.FixedBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.write.InMemoryBackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinator
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinatorFactory
import com.me4hik.praktika.data.backup.write.RecordingBackupStorage
import com.me4hik.praktika.data.backup.write.ambiguousRead
import com.me4hik.praktika.data.backup.write.envelopeWithSequence
import com.me4hik.praktika.data.backup.write.invalidRead
import com.me4hik.praktika.data.backup.write.practiceStateWithNextCyclePosition
import com.me4hik.praktika.data.backup.write.unreadableRead
import com.me4hik.praktika.data.backup.write.validRead
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage63ABackupFolderSetupCoordinatorTest {
    private val folderA = "content://com.test/tree/folder-a"
    private val folderB = "content://com.test/tree/folder-b"
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload
    private val clockEpoch = 1_800_000_000_000L

    @Test
    fun equalValid_noReceipt_forceWritesOnce() = runTest {
        val existing = envelopeWithSequence(5L)
        val storage = RecordingBackupStorage(
            slotB = validRead(BackupSlotId.B, existing),
        )
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 6L, "x")
        val h = harness(candidateUri = folderB, candidateStorage = storage)

        val inspect = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        val ready = inspect as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.VALID_EQUAL, ready.inspection.classification)

        val verified = h.coordinator.verifyWrite(ready.inspection.token) as SetupVerifyWriteResult.Verified
        assertEquals(1, storage.writeCalls.size)
        assertEquals(6L, storage.writeCalls.single().sequence)
        assertEquals(verified.receipt.sequence, storage.writeCalls.single().sequence)
        assertNull(h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun emptySetup_commit_authAndCatchupOnce() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(auth = null, candidateUri = folderB, candidateStorage = storage)

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.EMPTY, ready.inspection.classification)

        val verified = h.coordinator.verifyWrite(ready.inspection.token) as SetupVerifyWriteResult.Verified
        assertEquals(1L, verified.receipt.sequence)

        val commit = h.coordinator.commitVerifiedCandidate() as SetupCommitResult.Success
        assertEquals(verified.receipt.createdAtEpochMillis, commit.verifiedCreatedAtEpochMillis)
        assertTrue(commit.postSwitchCatchupScheduled)
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertEquals(folderB, h.repo.snapshot().treeUriHint)
        assertEquals(verified.receipt.createdAtEpochMillis, h.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertNull(h.repo.snapshot().lastFailureCategory)
        assertFalse(h.repo.snapshot().needsReconnect)
        assertEquals(listOf(BackupRequestReason.MANUAL to BackupAttemptTrigger.MANUAL), h.catchupRequests.toList())
    }

    @Test
    fun replacementSuccess_oldAuthUntilCommit_thenCatchupOnce() = runTest {
        val authStorage = RecordingBackupStorage()
        val candidate = RecordingBackupStorage()
        candidate.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(
            auth = folderA,
            candidateUri = folderB,
            candidateStorage = candidate,
            authStorage = authStorage,
        )
        authStorage.snapshotInspectionState()

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)

        val verified = h.coordinator.verifyWrite(ready.inspection.token) as SetupVerifyWriteResult.Verified
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
        assertTrue(authStorage.inspectionUnchanged())

        val commit = h.coordinator.commitVerifiedCandidate() as SetupCommitResult.Success
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertEquals(verified.receipt.createdAtEpochMillis, h.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertTrue(commit.postSwitchCatchupScheduled)
        assertEquals(1, h.catchupRequests.size)
        assertEquals(BackupRequestReason.MANUAL, h.catchupRequests.single().first)
        assertEquals(BackupAttemptTrigger.MANUAL, h.catchupRequests.single().second)
        assertTrue(authStorage.inspectionUnchanged())
        assertEquals(0, authStorage.deleteCallCount)
    }

    @Test
    fun replacementWriteFailure_preservesOldAuth() = runTest {
        val candidate = RecordingBackupStorage()
        candidate.writeResult = BackupSlotWriteResult.WriteFailed(BackupSlotId.A, "boom")
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = candidate)

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        val result = h.coordinator.verifyWrite(ready.inspection.token)
        assertEquals(SetupVerifyWriteResult.WriteFailed, result)
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
        assertEquals(0, h.catchupRequests.size)
        assertEquals(0, h.access.releaseCount)
    }

    @Test
    fun bothUntrusted_noWriteNoAuth() = runTest {
        val storage = RecordingBackupStorage(
            slotA = invalidRead(BackupSlotId.A),
            slotB = invalidRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val h = harness(candidateUri = folderB, candidateStorage = storage)
        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.BOTH_UNTRUSTED, ready.inspection.classification)
        assertEquals(
            SetupVerifyWriteResult.Blocked(SetupCandidateClassification.BOTH_UNTRUSTED),
            h.coordinator.verifyWrite(ready.inspection.token),
        )
        assertTrue(storage.writeCalls.isEmpty())
        assertTrue(storage.inspectionUnchanged())
        assertNull(h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun ambiguous_noWriteNoAuth() = runTest {
        val storage = RecordingBackupStorage(
            slotA = ambiguousRead(BackupSlotId.A),
            slotB = SlotReadResult.Missing,
        )
        storage.snapshotInspectionState()
        val h = harness(candidateUri = folderB, candidateStorage = storage)
        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.AMBIGUOUS, ready.inspection.classification)
        assertEquals(
            SetupVerifyWriteResult.Blocked(SetupCandidateClassification.AMBIGUOUS),
            h.coordinator.verifyWrite(ready.inspection.token),
        )
        assertTrue(storage.writeCalls.isEmpty())
        assertTrue(storage.inspectionUnchanged())
    }

    @Test
    fun validPlusUnreadable_blocksSetup() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(3L)),
            slotB = unreadableRead(BackupSlotId.B),
        )
        storage.snapshotInspectionState()
        val h = harness(candidateUri = folderB, candidateStorage = storage)
        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.VALID_PLUS_UNREADABLE, ready.inspection.classification)
        assertEquals(
            SetupVerifyWriteResult.Blocked(SetupCandidateClassification.VALID_PLUS_UNREADABLE),
            h.coordinator.verifyWrite(ready.inspection.token),
        )
        assertTrue(storage.writeCalls.isEmpty())
        assertTrue(storage.inspectionUnchanged())
    }

    @Test
    fun badPlusMissing_writesOppositePreservesBad() = runTest {
        val bad = invalidRead(BackupSlotId.A)
        val storage = RecordingBackupStorage(
            slotA = bad,
            slotB = SlotReadResult.Missing,
        )
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.B, 1L, "x")
        val h = harness(candidateUri = folderB, candidateStorage = storage)

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.INVALID_PLUS_MISSING, ready.inspection.classification)
        assertTrue(h.coordinator.verifyWrite(ready.inspection.token) is SetupVerifyWriteResult.Verified)
        assertEquals(1, storage.writeCalls.size)
        assertEquals(BackupSlotId.B, storage.writeCalls.single().slot)
        assertEquals(bad, storage.slotA)
    }

    @Test
    fun commitFailure_retainsReceiptAndGrant_noCatchup() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = storage)
        h.repo.commitAuthorizedShouldThrow = true

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertTrue(h.coordinator.verifyWrite(ready.inspection.token) is SetupVerifyWriteResult.Verified)
        assertEquals(SetupCommitResult.CommitFailed, h.coordinator.commitVerifiedCandidate())
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
        assertEquals(1, storage.writeCalls.size)
        assertTrue(storage.slotA is SlotReadResult.Valid)
        assertTrue(h.access.hasPersistedGrant(folderB))
        assertEquals(0, h.catchupRequests.size)
    }

    @Test
    fun sameSessionCommitRetry_noSecondWrite() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = storage)
        h.repo.commitAuthorizedShouldThrow = true

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        val verified = h.coordinator.verifyWrite(ready.inspection.token) as SetupVerifyWriteResult.Verified
        assertEquals(SetupCommitResult.CommitFailed, h.coordinator.commitVerifiedCandidate())
        assertEquals(1, storage.writeCalls.size)

        h.repo.commitAuthorizedShouldThrow = false
        val commit = h.coordinator.commitVerifiedCandidate() as SetupCommitResult.Success
        assertEquals(1, storage.writeCalls.size)
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertEquals(verified.receipt.createdAtEpochMillis, h.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertEquals(1, h.catchupRequests.size)
        assertTrue(commit.postSwitchCatchupScheduled)
    }

    @Test
    fun staleReceipt_rejectsCommitOnly() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(candidateUri = folderB, candidateStorage = storage)
        h.repo.commitAuthorizedShouldThrow = true

        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertTrue(h.coordinator.verifyWrite(ready.inspection.token) is SetupVerifyWriteResult.Verified)
        assertEquals(SetupCommitResult.CommitFailed, h.coordinator.commitVerifiedCandidate())

        storage.replaceSlotForTest(
            BackupSlotId.A,
            validRead(BackupSlotId.A, envelopeWithSequence(99L)),
        )
        h.repo.commitAuthorizedShouldThrow = false
        assertEquals(SetupCommitResult.ReceiptStale, h.coordinator.commitVerifiedCandidate())
        assertNull(h.repo.snapshot().authorizedTreeUri)
        assertEquals(1, storage.writeCalls.size)
    }

    @Test
    fun receiptLoss_newSession_mustForceWriteAgain() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h1 = harness(candidateUri = folderB, candidateStorage = storage)
        val ready1 = h1.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertTrue(h1.coordinator.verifyWrite(ready1.inspection.token) is SetupVerifyWriteResult.Verified)
        assertEquals(1, storage.writeCalls.size)

        val h2 = harness(candidateUri = folderB, candidateStorage = storage, sharedGate = h1.gate)
        assertEquals(SetupCommitResult.ReceiptMissing, h2.coordinator.commitVerifiedCandidate())
        val ready2 = h2.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.VALID_EQUAL, ready2.inspection.classification)
        assertTrue(h2.coordinator.verifyWrite(ready2.inspection.token) is SetupVerifyWriteResult.Verified)
        assertEquals(2, storage.writeCalls.size)
    }

    @Test
    fun roomChangeAfterReceipt_commitAllowed_catchupTargetsAuth() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val payloadRef = AtomicReference(goldenPayload)
        val h = harness(
            candidateUri = folderB,
            candidateStorage = storage,
            exportPayloadProvider = { payloadRef.get() },
        )
        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertTrue(h.coordinator.verifyWrite(ready.inspection.token) is SetupVerifyWriteResult.Verified)
        assertEquals(1, storage.writeCalls.size)
        payloadRef.set(practiceStateWithNextCyclePosition(goldenPayload, nextCyclePosition = 42))
        val commit = h.coordinator.commitVerifiedCandidate() as SetupCommitResult.Success
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertTrue(commit.postSwitchCatchupScheduled)
        assertEquals(1, h.catchupRequests.size)
        assertEquals(BackupRequestReason.MANUAL, h.catchupRequests.single().first)
        assertEquals(BackupAttemptTrigger.MANUAL, h.catchupRequests.single().second)
    }

    @Test
    fun abandon_releasesUnboundCandidateGrant() = runTest {
        val storage = RecordingBackupStorage()
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = storage)
        h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertTrue(h.access.hasPersistedGrant(folderB))
        assertEquals(SetupAbandonResult.Success, h.coordinator.abandonSession())
        assertFalse(h.access.hasPersistedGrant(folderB))
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
        assertEquals(SetupCommitResult.ReceiptMissing, h.coordinator.commitVerifiedCandidate())
    }

    @Test
    fun neverReleaseCurrentAuthGrant_whenCandidateEqualsAuth() = runTest {
        val storage = RecordingBackupStorage()
        val h = harness(auth = folderA, candidateUri = folderA, candidateStorage = storage)
        h.coordinator.inspectCandidate(folderA, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        assertEquals(0, h.access.releaseCount)
        h.coordinator.abandonSession()
        assertEquals(0, h.access.releaseCount)
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun disable_inflightWriter_authClearedBeforeReleaseUnderGate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authStorage = RecordingBackupStorage()
        authStorage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val writerInGate = CompletableDeferred<Unit>()
        val allowExit = CompletableDeferred<Unit>()
        authStorage.onWriteSuspend = {
            writerInGate.complete(Unit)
            allowExit.await()
        }
        val h = harness(
            auth = folderA,
            candidateUri = folderB,
            candidateStorage = RecordingBackupStorage(),
            authStorage = authStorage,
            dispatcher = dispatcher,
        )
        h.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(authStorage))

        val writeJob = async(dispatcher) {
            h.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        }
        writerInGate.await()

        val disableJob = async(dispatcher) {
            h.coordinator.disableAutomaticBackupAndReleasePermission()
        }
        advanceUntilIdle()
        // Auth clear happens before gate wait; writer still holds gate.
        assertNull(h.repo.snapshot().authorizedTreeUri)
        assertEquals(0, h.access.releaseCount)

        allowExit.complete(Unit)
        advanceUntilIdle()
        val disableResult = disableJob.await()
        writeJob.await()
        assertEquals(DisableAutomaticBackupResult.Success, disableResult)
        assertEquals(1, h.access.releaseCount)
        assertEquals(folderA, h.access.released.single())
    }

    @Test
    fun disable_inflightStatus_staleAttempt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authStorage = RecordingBackupStorage()
        authStorage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val writerInGate = CompletableDeferred<Unit>()
        val allowExit = CompletableDeferred<Unit>()
        authStorage.onWriteSuspend = {
            writerInGate.complete(Unit)
            allowExit.await()
        }
        val h = harness(
            auth = folderA,
            candidateUri = folderB,
            candidateStorage = RecordingBackupStorage(),
            authStorage = authStorage,
            dispatcher = dispatcher,
        )
        h.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(authStorage))

        val writeJob = async(dispatcher) {
            h.service.backupNow(BackupRequestReason.MANUAL, BackupAttemptTrigger.MANUAL)
        }
        writerInGate.await()
        val disableJob = async(dispatcher) {
            h.coordinator.disableAutomaticBackupAndReleasePermission()
        }
        advanceUntilIdle()
        assertNull(h.repo.snapshot().authorizedTreeUri)
        allowExit.complete(Unit)
        advanceUntilIdle()
        val writeResult = writeJob.await()
        disableJob.await()
        assertEquals(ConditionalStatusUpdateResult.StaleAttempt, writeResult.statusUpdate)
        assertNull(h.repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun disable_preservesFiles_noDelete() = runTest {
        val storage = RecordingBackupStorage(
            slotA = validRead(BackupSlotId.A, envelopeWithSequence(2L)),
        )
        storage.snapshotInspectionState()
        val h = harness(auth = folderA, candidateUri = folderA, candidateStorage = storage)
        assertEquals(
            DisableAutomaticBackupResult.Success,
            h.coordinator.disableAutomaticBackupAndReleasePermission(),
        )
        assertTrue(storage.inspectionUnchanged())
        assertEquals(0, storage.deleteCallCount)
        assertNull(h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun disable_lostGrant_succeeds() = runTest {
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = RecordingBackupStorage())
        h.repo.replace(
            BackupWriteState(
                treeUriHint = folderA,
                authorizedTreeUri = folderA,
                lastSuccessfulBackupAtEpochMillis = 1L,
                lastFailureCategory = null,
                needsReconnect = true,
            ),
        )
        h.access.pretendGrantAbsent = true
        assertEquals(
            DisableAutomaticBackupResult.Success,
            h.coordinator.disableAutomaticBackupAndReleasePermission(),
        )
        assertNull(h.repo.snapshot().authorizedTreeUri)
        assertFalse(h.repo.snapshot().needsReconnect)
    }

    @Test
    fun disable_releaseFailure_partialSuccessNoRollback() = runTest {
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = RecordingBackupStorage())
        h.access.releaseThrows = IllegalStateException("provider")
        val result = h.coordinator.disableAutomaticBackupAndReleasePermission()
        assertEquals(DisableAutomaticBackupResult.DisabledWithPermissionCleanupWarning, result)
        assertNull(h.repo.snapshot().authorizedTreeUri)
        assertEquals(folderA, h.repo.snapshot().treeUriHint)
    }

    @Test
    fun disable_datastoreFailure_noGrantRelease() = runTest {
        val h = harness(auth = folderA, candidateUri = folderB, candidateStorage = RecordingBackupStorage())
        h.repo.disableAutomaticBackupShouldThrow = true
        assertEquals(
            DisableAutomaticBackupResult.AuthClearFailed,
            h.coordinator.disableAutomaticBackupAndReleasePermission(),
        )
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
        assertEquals(0, h.access.releaseCount)
    }

    @Test
    fun sharedGateIdentity_matchesService() = runTest {
        val h = harness(candidateUri = folderB, candidateStorage = RecordingBackupStorage())
        assertSame(h.gate, h.coordinator.sharedIoGateForTests)
        assertSame(h.gate, h.service.sharedIoGate)
    }

    @Test
    fun autoWriterDuringCandidateSetup_serializedOnSharedGate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authStorage = RecordingBackupStorage()
        authStorage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val candidate = RecordingBackupStorage()
        candidate.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val writerInGate = CompletableDeferred<Unit>()
        val allowExit = CompletableDeferred<Unit>()
        authStorage.onWriteSuspend = {
            writerInGate.complete(Unit)
            allowExit.await()
        }
        val gate = BackupIoSessionGate()
        val h = harness(
            auth = folderA,
            candidateUri = folderB,
            candidateStorage = candidate,
            authStorage = authStorage,
            sharedGate = gate,
            dispatcher = dispatcher,
        )
        h.resolver.resultFor = mapOf(folderA to AuthorizedStorageResolveResult.Ready(authStorage))

        val writeJob = async(dispatcher) {
            h.service.backupNow(BackupRequestReason.ANSWER_SAVED, BackupAttemptTrigger.MUTATION)
        }
        writerInGate.await()

        val inspectJob = async(dispatcher) {
            h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
        }
        advanceUntilIdle()
        assertFalse(inspectJob.isCompleted)
        assertEquals(1, gate.exclusiveDepthForTests)

        allowExit.complete(Unit)
        advanceUntilIdle()
        writeJob.await()
        val inspect = inspectJob.await() as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.EMPTY, inspect.inspection.classification)
        assertTrue(gate.maxExclusiveDepthForTests <= 1)
    }

    @Test
    fun postSwitchRace_catchupManualOnce_notToOldAuth() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val payloadRef = AtomicReference(goldenPayload)
        val h = harness(
            auth = folderA,
            candidateUri = folderB,
            candidateStorage = storage,
            exportPayloadProvider = { payloadRef.get() },
        )
        val ready = h.coordinator.inspectCandidate(folderB, BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS)
            as SetupInspectResult.Ready
        assertTrue(h.coordinator.verifyWrite(ready.inspection.token) is SetupVerifyWriteResult.Verified)
        payloadRef.set(practiceStateWithNextCyclePosition(goldenPayload, 7))
        h.coordinator.commitVerifiedCandidate()
        assertEquals(1, h.catchupRequests.size)
        assertEquals(BackupRequestReason.MANUAL, h.catchupRequests.single().first)
        assertEquals(BackupAttemptTrigger.MANUAL, h.catchupRequests.single().second)
        advanceUntilIdle()
        assertTrue(h.resolver.resolvedUris.all { it == folderB })
    }

    @Test
    fun normalCoordinator_equalPayload_stillNoChange() = runTest {
        val latest = envelopeWithSequence(99L)
        val storage = RecordingBackupStorage(slotB = validRead(BackupSlotId.B, latest))
        storage.snapshotInspectionState()
        val coordinator = ProductionBackupCoordinator(
            storage = storage,
            exportAction = { BackupExportResult.Success(goldenPayload) },
            metadataFactory = BackupSnapshotMetadataFactory(
                clock = CountingBackupClock(clockEpoch),
                appMetadataProvider = FixedBackupAppMetadataProvider(),
            ),
            scope = this,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val outcome = coordinator.backupNow(BackupRequestReason.MANUAL)
        assertTrue(outcome is com.me4hik.praktika.data.backup.write.BackupOutcome.NoChange)
        assertTrue(storage.writeCalls.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.harness(
        auth: String? = null,
        candidateUri: String,
        candidateStorage: RecordingBackupStorage,
        authStorage: RecordingBackupStorage? = null,
        sharedGate: BackupIoSessionGate = BackupIoSessionGate(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        exportPayloadProvider: () -> PraktikaBackupPayload = { goldenPayload },
    ): Harness {
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = auth,
                authorizedTreeUri = auth,
                lastSuccessfulBackupAtEpochMillis = if (auth != null) 1L else null,
                lastFailureCategory = null,
                needsReconnect = false,
            ),
        )
        val access = FakeCandidateAccess(mapOf(candidateUri to candidateStorage))
        if (auth != null) {
            access.seedGrant(auth)
        }
        val catchupRequests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val resolver = RecordingResolver()
        if (authStorage != null && auth != null) {
            resolver.resultFor = mapOf(auth to AuthorizedStorageResolveResult.Ready(authStorage))
        }
        resolver.resultFor = resolver.resultFor + mapOf(
            candidateUri to AuthorizedStorageResolveResult.Ready(candidateStorage),
        )
        val service = AuthorizedBackupService(
            writeStateRepository = repo,
            storageResolver = resolver,
            coordinatorFactory = ProductionBackupCoordinatorFactory { storage ->
                ProductionBackupCoordinator(
                    storage = storage,
                    exportAction = { BackupExportResult.Success(exportPayloadProvider()) },
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
            exportAction = { BackupExportResult.Success(exportPayloadProvider()) },
            metadataFactory = BackupSnapshotMetadataFactory(
                clock = CountingBackupClock(clockEpoch),
                appMetadataProvider = FixedBackupAppMetadataProvider(),
            ),
        )
        return Harness(
            coordinator = coordinator,
            service = service,
            repo = repo,
            gate = sharedGate,
            access = access,
            catchupRequests = catchupRequests,
            resolver = resolver,
        )
    }

    private class Harness(
        val coordinator: BackupFolderSetupCoordinator,
        val service: AuthorizedBackupService,
        val repo: InMemoryBackupWriteStateRepository,
        val gate: BackupIoSessionGate,
        val access: FakeCandidateAccess,
        val catchupRequests: CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>,
        val resolver: RecordingResolver,
    )

    private class FakeCandidateAccess(
        private val storageByUri: Map<String, BackupStorageProvider>,
    ) : SetupCandidateAccess {
        private val grants = mutableSetOf<String>()
        val released = mutableListOf<String>()
        var releaseThrows: Exception? = null
        var pretendGrantAbsent: Boolean = false
        val releaseCount: Int get() = released.size

        fun seedGrant(uri: String) {
            grants += uri
        }

        override suspend fun connectTransient(
            uriString: String,
            grantFlags: Int,
        ): SetupCandidateConnectResult {
            if (!pretendGrantAbsent) {
                grants += uriString
            }
            return SetupCandidateConnectResult.Success
        }

        override fun createStorage(uriString: String): BackupStorageProvider? = storageByUri[uriString]

        override fun releasePersistedGrant(uriString: String, grantFlags: Int) {
            releaseThrows?.let { throw it }
            if (pretendGrantAbsent) {
                return
            }
            released += uriString
            grants -= uriString
        }

        override fun hasPersistedGrant(uriString: String): Boolean {
            if (pretendGrantAbsent) return false
            return uriString in grants
        }
    }

    private class RecordingResolver : AuthorizedBackupStorageResolver {
        var resultFor: Map<String, AuthorizedStorageResolveResult> = emptyMap()
        val resolvedUris = mutableListOf<String>()

        override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
            resolvedUris += authorizedUriString
            return resultFor[authorizedUriString] ?: AuthorizedStorageResolveResult.Unavailable
        }
    }
}
