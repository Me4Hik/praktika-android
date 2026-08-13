package com.me4hik.praktika.ui.restore

import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinator
import com.me4hik.praktika.data.backup.restore.RestoreRoomTestSupport
import com.me4hik.praktika.data.backup.restore.RoomBackupRestorer
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupTreeAccessValidator
import com.me4hik.praktika.data.backup.storage.InMemoryBackupTreeConfigRepository
import com.me4hik.praktika.data.backup.storage.PersistableTreePermissionGrant
import com.me4hik.praktika.data.backup.storage.PersistableTreePermissionRelease
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.backup.restore.NotificationSyncObservation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DefaultProductionRestoreGatewayPersistenceTest : RestoreRoomTestSupport() {
    private val folderA = Uri.parse("content://com.test/tree/folder-a")
    private val folderB = Uri.parse("content://com.test/tree/folder-b")
    private val grantFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Before
    fun setUp() {
        setUpDatabase()
    }

    @After
    fun tearDown() {
        tearDownDatabase()
    }

    @Test
    fun connectFolder_usesTransientConnectWithoutPersistingHint() = runTest {
        val repository = InMemoryBackupTreeConfigRepository()
        val gateway = gateway(
            repository = repository,
            validator = ConnectedValidator(folderA),
        )

        val outcome = gateway.connectFolder(folderA, grantFlags)

        assertTrue(outcome is ProductionRestoreConnectOutcome.Success)
        assertNull(repository.treeUriHint.first())
    }

    @Test
    fun commitActiveBackupFolder_persistsCandidateUri() = runTest {
        val repository = InMemoryBackupTreeConfigRepository()
        val gateway = gateway(
            repository = repository,
            validator = ConnectedValidator(folderA),
        )

        assertTrue(gateway.commitActiveBackupFolder(folderA))
        assertEquals(folderA.toString(), repository.treeUriHint.first())
    }

    @Test
    fun releaseRejectedTransientGrant_doesNotReleaseActiveFolderGrant() = runTest {
        val released = mutableListOf<Uri>()
        val repository = InMemoryBackupTreeConfigRepository()
        repository.saveTreeUri(folderA.toString())
        val gateway = gateway(
            repository = repository,
            validator = ConnectedValidator(folderA),
            onRelease = { uri, _ -> released += uri },
        )

        gateway.releaseRejectedTransientGrant(folderA, grantFlags)

        assertTrue(released.isEmpty())
    }

    @Test
    fun releaseRejectedTransientGrant_releasesNonActiveCandidate() = runTest {
        val released = mutableListOf<Uri>()
        val repository = InMemoryBackupTreeConfigRepository()
        repository.saveTreeUri(folderA.toString())
        val gateway = gateway(
            repository = repository,
            validator = ConnectedValidator(folderB),
            onRelease = { uri, _ -> released += uri },
        )

        gateway.releaseRejectedTransientGrant(folderB, grantFlags)

        assertEquals(listOf(folderB), released)
        assertEquals(folderA.toString(), repository.treeUriHint.first())
    }

    private fun gateway(
        repository: InMemoryBackupTreeConfigRepository,
        validator: BackupTreeAccessValidator,
        onRelease: (Uri, Int) -> Unit = { _, _ -> },
    ): DefaultProductionRestoreGateway {
        val folderConnection = BackupFolderConnection(
            treeConfigRepository = repository,
            accessValidator = validator,
            permissionGrant = PersistableTreePermissionGrant { _, _ -> },
            permissionRelease = PersistableTreePermissionRelease { uri, flags ->
                onRelease(uri, flags)
            },
        )
        val coordinator = BackupRestoreCoordinator(
            database = database,
            restorer = RoomBackupRestorer(database),
            reconcileAction = { CycleResult.ReconcileNoChanges },
            notificationSyncAction = { NotificationSyncObservation.Invoked },
        )
        return DefaultProductionRestoreGateway(
            coordinator = coordinator,
            folderConnection = folderConnection,
            contentResolver = org.robolectric.RuntimeEnvironment.getApplication().contentResolver,
            treeConfigRepository = repository,
            ioGate = com.me4hik.praktika.data.backup.write.BackupIoSessionGate(),
        )
    }
}

private class ConnectedValidator(
    private val treeUri: Uri,
) : BackupTreeAccessValidator {
    override fun validate(treeUri: Uri): BackupFolderAccessState {
        return if (treeUri == this.treeUri) {
            BackupFolderAccessState.Connected(treeUri)
        } else {
            BackupFolderAccessState.Unavailable
        }
    }
}
