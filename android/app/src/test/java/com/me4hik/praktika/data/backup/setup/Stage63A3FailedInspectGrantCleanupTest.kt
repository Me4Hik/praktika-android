package com.me4hik.praktika.data.backup.setup

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.AuthorizedBackupStorageResolver
import com.me4hik.praktika.data.backup.write.AuthorizedStorageResolveResult
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupWriteState
import com.me4hik.praktika.data.backup.write.CountingBackupClock
import com.me4hik.praktika.data.backup.write.FixedBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.write.InMemoryBackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinator
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinatorFactory
import com.me4hik.praktika.data.backup.write.RecordingBackupStorage
import com.me4hik.praktika.data.backup.write.ambiguousRead
import com.me4hik.praktika.data.backup.write.invalidRead
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Stage63A3FailedInspectGrantCleanupTest {
    private val folderA = "content://com.test/tree/folder-a"
    private val folderB = "content://com.test/tree/folder-b"
    private val folderC = "content://com.test/tree/folder-c"
    private val goldenPayload = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload
    private val clockEpoch = 1_800_000_000_000L
    private val flags = BackupFolderSetupCoordinator.DEFAULT_RW_FLAGS

    @Test
    fun unbound_unavailable_afterTake_releasesCandidate_keepsOtherAuth() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertEquals(listOf(folderC), h.access.released)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertTrue(h.access.hasPersistedGrant(folderB))
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun unbound_providerFailure_afterTake_releasesOnce() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.ProviderFailure
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.ProviderFailure,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun unbound_permissionLost_afterTake_releasesOnce() = runTest {
        // Fake: take succeeds then connect returns PermissionLost (validate-after-take).
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.PermissionLost
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.PermissionLost,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun takeItselfFails_noGrant_authUnchanged() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.PermissionLost
        h.access.takeOnConnect = false

        assertEquals(
            SetupInspectResult.PermissionLost,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(0, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertTrue(h.access.hasPersistedGrant(folderB))
    }

    @Test
    fun authorizedCurrent_fail_keepsGrant() = runTest {
        val h = harness(auth = folderA, candidateUri = folderA)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderA, flags),
        )
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderA))
        assertEquals(folderA, h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun authorizedAtStart_authSwitchesToOther_keepsOldGrant() = runTest {
        val h = harness(auth = folderA, candidateUri = folderA)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true
        h.access.afterTake = {
            h.repo.setAuthorized(folderB)
            h.access.seedGrant(folderB)
        }

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderA, flags),
        )
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderA))
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun authorizedAtStart_authBecomesNull_releases() = runTest {
        val h = harness(auth = folderA, candidateUri = folderA)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true
        h.access.afterTake = {
            h.repo.setAuthorized(null)
        }

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderA, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderA))
        assertNull(h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun unbound_becomesCurrentAuth_keeps() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true
        h.access.afterTake = {
            h.repo.setAuthorized(folderC)
        }

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderC))
        assertEquals(folderC, h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun unbound_authOther_releases() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
    }

    @Test
    fun unbound_authNull_releases() = runTest {
        val h = harness(auth = null, candidateUri = folderC)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertNull(h.repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun disableReacquire_thenFail_finalGrantAbsent() = runTest {
        val h = harness(auth = null, candidateUri = folderC)
        assertFalse(h.access.hasPersistedGrant(folderC))
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertNull(h.repo.snapshot().authorizedTreeUri)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertEquals(1, h.access.releaseCount)
    }

    @Test
    fun createStorageNull_afterSuccessfulConnect_releasesUnbound() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC, omitCandidateStorage = true)
        h.access.connectResult = SetupCandidateConnectResult.Success
        h.access.takeOnConnect = true

        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun cancellationAfterTake_unbound_releasesAndRethrows() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.takeOnConnect = true
        h.access.throwCancellationAfterTake = true

        try {
            h.coordinator.inspectCandidate(folderC, flags)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertTrue(h.access.hasPersistedGrant(folderB))
        assertEquals(folderB, h.repo.snapshot().authorizedTreeUri)
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun cancellationAfterTake_currentAuth_keepsAndRethrows() = runTest {
        val h = harness(auth = folderA, candidateUri = folderA)
        h.access.takeOnConnect = true
        h.access.throwCancellationAfterTake = true

        try {
            h.coordinator.inspectCandidate(folderA, flags)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderA))
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun readySuccess_noPreSessionRelease_abandonReleasesOnce() = runTest {
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "x")
        val h = harness(auth = folderB, candidateUri = folderC, candidateStorage = storage)

        val ready = h.coordinator.inspectCandidate(folderC, flags) as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.EMPTY, ready.inspection.classification)
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderC))
        assertTrue(h.coordinator.hasCandidateSessionForTests())

        assertEquals(SetupAbandonResult.Success, h.coordinator.abandonSession())
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
        assertFalse(h.coordinator.hasCandidateSessionForTests())
    }

    @Test
    fun blockedReady_bothUntrusted_noPreSessionRelease_abandonOnce() = runTest {
        val storage = RecordingBackupStorage(
            slotA = invalidRead(BackupSlotId.A),
            slotB = invalidRead(BackupSlotId.B),
        )
        val h = harness(auth = folderB, candidateUri = folderC, candidateStorage = storage)

        val ready = h.coordinator.inspectCandidate(folderC, flags) as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.BOTH_UNTRUSTED, ready.inspection.classification)
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderC))

        assertEquals(SetupAbandonResult.Success, h.coordinator.abandonSession())
        assertEquals(1, h.access.releaseCount)
    }

    @Test
    fun blockedReady_ambiguous_noPreSessionRelease() = runTest {
        val storage = RecordingBackupStorage(
            slotA = ambiguousRead(BackupSlotId.A),
            slotB = ambiguousRead(BackupSlotId.B),
        )
        val h = harness(auth = null, candidateUri = folderC, candidateStorage = storage)

        val ready = h.coordinator.inspectCandidate(folderC, flags) as SetupInspectResult.Ready
        assertEquals(SetupCandidateClassification.AMBIGUOUS, ready.inspection.classification)
        assertEquals(0, h.access.releaseCount)
        assertTrue(h.access.hasPersistedGrant(folderC))
    }

    @Test
    fun preexistingUnboundGrant_fail_cleansGrant() = runTest {
        val h = harness(auth = folderB, candidateUri = folderC)
        h.access.seedGrant(folderC)
        h.access.connectResult = SetupCandidateConnectResult.Unavailable
        h.access.takeOnConnect = false

        assertTrue(h.access.hasPersistedGrant(folderC))
        assertEquals(
            SetupInspectResult.Unavailable,
            h.coordinator.inspectCandidate(folderC, flags),
        )
        assertEquals(1, h.access.releaseCount)
        assertFalse(h.access.hasPersistedGrant(folderC))
    }

    private fun kotlinx.coroutines.test.TestScope.harness(
        auth: String?,
        candidateUri: String,
        candidateStorage: RecordingBackupStorage = RecordingBackupStorage(),
        omitCandidateStorage: Boolean = false,
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
        val storageMap = if (omitCandidateStorage) {
            emptyMap()
        } else {
            mapOf(candidateUri to candidateStorage)
        }
        val access = ControllableCandidateAccess(storageMap)
        if (auth != null) {
            access.seedGrant(auth)
        }
        val gate = BackupIoSessionGate()
        val resolver = object : AuthorizedBackupStorageResolver {
            override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
                return AuthorizedStorageResolveResult.Unavailable
            }
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
                    ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                )
            },
            scope = this,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            ioGate = gate,
        )
        val coordinator = BackupFolderSetupCoordinator(
            writeStateRepository = repo,
            ioGate = gate,
            authorizedBackupService = service,
            candidateAccess = access,
            exportAction = { BackupExportResult.Success(goldenPayload) },
            metadataFactory = BackupSnapshotMetadataFactory(
                clock = CountingBackupClock(clockEpoch),
                appMetadataProvider = FixedBackupAppMetadataProvider(),
            ),
        )
        return Harness(coordinator, repo, access)
    }

    private class Harness(
        val coordinator: BackupFolderSetupCoordinator,
        val repo: InMemoryBackupWriteStateRepository,
        val access: ControllableCandidateAccess,
    )

    private class ControllableCandidateAccess(
        private val storageByUri: Map<String, BackupStorageProvider>,
    ) : SetupCandidateAccess {
        private val grants = mutableSetOf<String>()
        val released = mutableListOf<String>()
        val releaseCount: Int get() = released.size

        var connectResult: SetupCandidateConnectResult = SetupCandidateConnectResult.Success
        var takeOnConnect: Boolean = true
        var throwCancellationAfterTake: Boolean = false
        var afterTake: (suspend () -> Unit)? = null

        fun seedGrant(uri: String) {
            grants += uri
        }

        override suspend fun connectTransient(
            uriString: String,
            grantFlags: Int,
        ): SetupCandidateConnectResult {
            if (takeOnConnect) {
                grants += uriString
            }
            afterTake?.invoke()
            if (throwCancellationAfterTake) {
                throw CancellationException("test-cancel-after-take")
            }
            return connectResult
        }

        override fun createStorage(uriString: String): BackupStorageProvider? = storageByUri[uriString]

        override fun releasePersistedGrant(uriString: String, grantFlags: Int) {
            if (uriString !in grants) {
                return
            }
            released += uriString
            grants -= uriString
        }

        override fun hasPersistedGrant(uriString: String): Boolean = uriString in grants
    }
}
