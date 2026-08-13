// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.1 backup folder transient connect tests
package com.me4hik.praktika.data.backup.storage

import android.content.Intent
import android.net.Uri
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BackupFolderConnectionTransientTest {
    private val treeUri = testTreeUri()
    private val grantFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Test
    fun connect_persistsTreeUriHint() = runTest {
        val repository = recordingRepository()
        val connection = connection(repository)

        val result = connection.connect(treeUri, grantFlags)

        assertTrue(result is BackupFolderConnectionResult.Success)
        assertEquals(treeUri.toString(), repository.hint.first())
    }

    @Test
    fun connectTransient_doesNotPersistTreeUriHint() = runTest {
        val repository = recordingRepository()
        val connection = connection(repository)

        val result = connection.connectTransient(treeUri, grantFlags)

        assertTrue(result is BackupFolderConnectionResult.Success)
        assertNull(repository.hint.first())
    }

    @Test
    fun connectTransient_successStillValidatesAccess() = runTest {
        val repository = recordingRepository()
        val connection = connection(
            repository = repository,
            accessValidator = ConnectedAccessValidator(treeUri),
        )

        val result = connection.connectTransient(treeUri, grantFlags)

        assertTrue(result is BackupFolderConnectionResult.Success)
        assertEquals(treeUri, (result as BackupFolderConnectionResult.Success).treeUri)
    }

    @Test
    fun connectTransient_failureDoesNotPersistHint() = runTest {
        val repository = recordingRepository()
        val connection = connection(
            repository = repository,
            accessValidator = UnavailableAccessValidator,
        )

        val result = connection.connectTransient(treeUri, grantFlags)

        assertEquals(BackupFolderConnectionResult.Unavailable, result)
        assertNull(repository.hint.first())
    }

    @Test
    fun releasePersistedPermission_invokesReleaseCallback() = runTest {
        val releaseCalls = AtomicInteger(0)
        val connection = connection(
            repository = recordingRepository(),
            permissionRelease = PersistableTreePermissionRelease { uri, flags ->
                releaseCalls.incrementAndGet()
                assertEquals(treeUri, uri)
                assertEquals(grantFlags, flags)
            },
        )

        connection.releasePersistedPermission(treeUri, grantFlags)

        assertEquals(1, releaseCalls.get())
    }

    private fun connection(
        repository: BackupTreeConfigRepository,
        accessValidator: BackupTreeAccessValidator = ConnectedAccessValidator(treeUri),
        permissionRelease: PersistableTreePermissionRelease = PersistableTreePermissionRelease { _, _ -> },
    ): BackupFolderConnection {
        return BackupFolderConnection(
            treeConfigRepository = repository,
            accessValidator = accessValidator,
            permissionGrant = PersistableTreePermissionGrant { _, _ -> },
            permissionRelease = permissionRelease,
        )
    }

    private fun recordingRepository(): RecordingBackupTreeConfigRepository {
        return RecordingBackupTreeConfigRepository()
    }
}

private class RecordingBackupTreeConfigRepository : BackupTreeConfigRepository {
    private val hintFlow = MutableStateFlow<String?>(null)
    override val treeUriHint = hintFlow

    val hint = hintFlow

    override suspend fun saveTreeUri(uriString: String) {
        hintFlow.value = uriString
    }

    override suspend fun clearTreeUri() {
        hintFlow.value = null
    }
}

private object UnavailableAccessValidator : BackupTreeAccessValidator {
    override fun validate(treeUri: Uri): BackupFolderAccessState {
        return BackupFolderAccessState.Unavailable
    }
}

private fun testTreeUri(): Uri {
    return Uri.parse("content://com.test/tree/backup")
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
