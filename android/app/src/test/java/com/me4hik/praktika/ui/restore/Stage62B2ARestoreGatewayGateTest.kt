package com.me4hik.praktika.ui.restore

import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinator
import com.me4hik.praktika.data.backup.restore.BackupRestoreEngine
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.NotificationSyncObservation
import com.me4hik.praktika.data.backup.restore.RestoreRoomTestSupport
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.BackupTreeAccessValidator
import com.me4hik.praktika.data.backup.storage.InMemoryBackupTreeConfigRepository
import com.me4hik.praktika.data.backup.storage.PersistableTreePermissionGrant
import com.me4hik.praktika.data.backup.storage.PersistableTreePermissionRelease
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.cycle.CycleResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class Stage62B2ARestoreGatewayGateTest : RestoreRoomTestSupport() {
    private val treeUri = Uri.parse("content://com.test/tree/gated")
    private val grantFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Before
    fun setUp() {
        setUpDatabase()
    }

    @Test
    fun inspectLatest_usesGate_andReleases() = runTest {
        val gate = BackupIoSessionGate()
        val envelope = BackupRestoreFixtures.richEnvelope()
        val storage = GatedProbeStorage(
            inspection = SlotInspectionResult(
                slotA = SlotReadResult.Missing,
                slotB = SlotReadResult.Valid(BackupSlotId.B, envelope),
            ),
        )
        val gateway = gateway(gate)
        val result = gateway.inspectLatest(storage)
        assertTrue(result is com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult.PreviewReady)
        assertEquals(1, gate.enterCountForTests)
        assertEquals(1, gate.maxExclusiveDepthForTests)
        assertEquals(0, gate.exclusiveDepthForTests)
    }

    @Test
    fun executeRestore_roomRunsOutsideGate() = runTest {
        val gate = BackupIoSessionGate()
        val envelope = BackupRestoreFixtures.richEnvelope()
        val preview = BackupRestorePreviewBuilder.fromEnvelope(BackupSlotId.B, envelope)
        var roomSawGateHeld = false
        val restorer = object : BackupRestoreEngine {
            override suspend fun restore(envelope: PraktikaBackupEnvelope): BackupRestoreResult {
                roomSawGateHeld = gate.exclusiveDepthForTests > 0
                return BackupRestoreResult.Success
            }
        }
        val storage = GatedProbeStorage(
            inspection = SlotInspectionResult(
                slotA = SlotReadResult.Missing,
                slotB = SlotReadResult.Valid(BackupSlotId.B, envelope),
            ),
        )
        val gateway = gateway(gate, restorer = restorer)
        val result = gateway.executeRestore(
            storage = storage,
            previewIdentity = preview.identity,
            preview = preview,
        )
        assertTrue(
            result is com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult.FullSuccess,
        )
        assertFalse(roomSawGateHeld)
        assertEquals(0, gate.exclusiveDepthForTests)
        assertEquals(1, gate.maxExclusiveDepthForTests)
    }

    @Test
    fun sharedGate_serializesInspectCalls_maxDepthOne() = runTest {
        val gate = BackupIoSessionGate()
        val envelope = BackupRestoreFixtures.richEnvelope()
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val storage = GatedProbeStorage(
            inspection = SlotInspectionResult(
                slotA = SlotReadResult.Missing,
                slotB = SlotReadResult.Valid(BackupSlotId.B, envelope),
            ),
            onInspect = {
                held.complete(Unit)
                release.await()
            },
        )
        val gateway = gateway(gate)
        val first = async { gateway.inspectLatest(storage) }
        held.await()
        val second = async { gateway.inspectLatest(storage) }
        assertEquals(1, gate.exclusiveDepthForTests)
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, gate.maxExclusiveDepthForTests)
        assertEquals(2, gate.enterCountForTests)
    }

    @Test
    fun gatewayReceivesInjectedGateIdentity() = runTest {
        val gate = BackupIoSessionGate()
        val gateway = gateway(gate)
        assertSame(gate, gateway.sharedIoGateForTests)
    }

    @Test
    fun connectAndInspectCandidate_singleCriticalSection() = runTest {
        val gate = BackupIoSessionGate()
        val gateway = gateway(gate)
        // Robolectric SAF storage will typically yield NoValidBackup; gate still wraps once.
        val result = gateway.connectAndInspectCandidate(treeUri, grantFlags)
        assertTrue(
            result is ProductionRestoreCandidateIoResult.Connected ||
                result is ProductionRestoreCandidateIoResult.PermissionLost ||
                result is ProductionRestoreCandidateIoResult.ReadFailure,
        )
        assertEquals(1, gate.enterCountForTests)
        assertEquals(1, gate.maxExclusiveDepthForTests)
        assertEquals(0, gate.exclusiveDepthForTests)
    }

    private fun gateway(
        gate: BackupIoSessionGate,
        restorer: BackupRestoreEngine = SucceedingRestorer(),
    ): DefaultProductionRestoreGateway {
        val repository = InMemoryBackupTreeConfigRepository()
        val folderConnection = BackupFolderConnection(
            treeConfigRepository = repository,
            accessValidator = object : BackupTreeAccessValidator {
                override fun validate(treeUri: Uri): BackupFolderAccessState {
                    return BackupFolderAccessState.Connected(treeUri)
                }
            },
            permissionGrant = PersistableTreePermissionGrant { _, _ -> },
            permissionRelease = PersistableTreePermissionRelease { _, _ -> },
        )
        val coordinator = BackupRestoreCoordinator(
            database = database,
            restorer = restorer,
            reconcileAction = { CycleResult.ReconcileNoChanges },
            notificationSyncAction = { NotificationSyncObservation.Invoked },
        )
        return DefaultProductionRestoreGateway(
            coordinator = coordinator,
            folderConnection = folderConnection,
            contentResolver = RuntimeEnvironment.getApplication().contentResolver,
            treeConfigRepository = repository,
            ioGate = gate,
        )
    }
}

private class SucceedingRestorer : BackupRestoreEngine {
    override suspend fun restore(envelope: PraktikaBackupEnvelope): BackupRestoreResult {
        return BackupRestoreResult.Success
    }
}

private class GatedProbeStorage(
    private val inspection: SlotInspectionResult,
    private val onInspect: (suspend () -> Unit)? = null,
) : BackupStorageProvider {
    override suspend fun inspectSlots(): SlotInspectionResult {
        onInspect?.invoke()
        return inspection
    }

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult {
        return when (slot) {
            BackupSlotId.A -> inspection.slotA
            BackupSlotId.B -> inspection.slotB
        }
    }

    override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult = BackupSlotWriteResult.CreateFailed(slot)
}
