// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening tests
package com.me4hik.praktika.data.backup.storage

import android.content.Intent
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CancellationException
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
class BackupFolderConnectionCancellationTest {
    private val treeUri = testTreeUri()

    @Test
    fun connect_saveCancellation_propagatesAndDoesNotPersistHint() = runTest {
        val hint = MutableStateFlow<String?>(null)
        val repository = object : BackupTreeConfigRepository {
            override val treeUriHint = hint

            override suspend fun saveTreeUri(uriString: String) {
                throw CancellationException("cancel during save")
            }

            override suspend fun clearTreeUri() {
                hint.value = null
            }
        }
        val connection = BackupFolderConnection(
            treeConfigRepository = repository,
            accessValidator = ConnectedAccessValidator(treeUri),
            permissionGrant = PersistableTreePermissionGrant { _, _ -> },
            permissionRelease = PersistableTreePermissionRelease { _, _ -> },
        )

        val thrown = runCatching {
            connection.connect(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertNull(hint.first())
    }

    @Test
    fun connect_permissionGrantFailure_stillStructuredProviderError() = runTest {
        val hint = MutableStateFlow<String?>(null)
        val repository = object : BackupTreeConfigRepository {
            override val treeUriHint = hint

            override suspend fun saveTreeUri(uriString: String) {
                hint.value = uriString
            }

            override suspend fun clearTreeUri() {
                hint.value = null
            }
        }
        val connection = BackupFolderConnection(
            treeConfigRepository = repository,
            accessValidator = ConnectedAccessValidator(treeUri),
            permissionGrant = PersistableTreePermissionGrant { _, _ ->
                throw IOException("grant failed")
            },
            permissionRelease = PersistableTreePermissionRelease { _, _ -> },
        )

        val result = connection.connect(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

        assertTrue(result is BackupFolderConnectionResult.ProviderError)
        assertEquals(IOException::class.java.name, (result as BackupFolderConnectionResult.ProviderError).exceptionClass)
        assertNull(hint.first())
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
