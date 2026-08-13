// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 SAF reconnect access
package com.me4hik.praktika.data.backup.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessValidator
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupTreeAccessValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production reconnect access: takePersistable then validate without auto-release on validation failure.
 */
class SafBackupReconnectAccess(
    private val contentResolver: ContentResolver,
    private val folderConnection: BackupFolderConnection,
    private val accessValidator: BackupTreeAccessValidator =
        BackupFolderAccessValidator(contentResolver),
) : BackupReconnectAccess {
    override suspend fun acquireAndValidate(
        uriString: String,
        grantFlags: Int,
    ): BackupReconnectAcquireResult = withContext(Dispatchers.IO) {
        val uri = parseContentUri(uriString) ?: return@withContext BackupReconnectAcquireResult.PermissionAcquireFailed
        val takeFlags = grantFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        if (takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 ||
            takeFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0
        ) {
            return@withContext BackupReconnectAcquireResult.PermissionAcquireFailed
        }
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
            return@withContext BackupReconnectAcquireResult.PermissionAcquireFailed
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return@withContext BackupReconnectAcquireResult.PermissionAcquireFailed
        }
        when (accessValidator.validate(uri)) {
            is BackupFolderAccessState.Connected ->
                BackupReconnectAcquireResult.AcquiredAndValid
            BackupFolderAccessState.PermissionLost,
            BackupFolderAccessState.Unavailable,
            is BackupFolderAccessState.ProviderError,
            -> BackupReconnectAcquireResult.ValidationFailed
        }
    }

    override fun releasePersistedGrant(uriString: String, grantFlags: Int) {
        val uri = parseContentUri(uriString) ?: return
        folderConnection.releasePersistedPermission(uri, grantFlags)
    }

    override fun hasPersistedGrant(uriString: String): Boolean {
        val uri = parseContentUri(uriString) ?: return false
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri &&
                permission.isReadPermission &&
                permission.isWritePermission
        }
    }

    private fun parseContentUri(uriString: String): Uri? {
        if (uriString.isBlank()) {
            return null
        }
        val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return null
        }
        return uri
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
