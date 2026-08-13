// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A SAF setup candidate access
package com.me4hik.praktika.data.backup.setup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupFolderConnectionResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider

class SafSetupCandidateAccess(
    private val contentResolver: ContentResolver,
    private val folderConnection: BackupFolderConnection,
) : SetupCandidateAccess {
    override suspend fun connectTransient(
        uriString: String,
        grantFlags: Int,
    ): SetupCandidateConnectResult {
        val uri = parseContentUri(uriString) ?: return SetupCandidateConnectResult.Unavailable
        return when (folderConnection.connectTransient(uri, grantFlags)) {
            is BackupFolderConnectionResult.Success -> SetupCandidateConnectResult.Success
            BackupFolderConnectionResult.PermissionLost -> SetupCandidateConnectResult.PermissionLost
            BackupFolderConnectionResult.Unavailable -> SetupCandidateConnectResult.Unavailable
            is BackupFolderConnectionResult.ProviderError -> SetupCandidateConnectResult.ProviderFailure
        }
    }

    override fun createStorage(uriString: String): BackupStorageProvider? {
        val uri = parseContentUri(uriString) ?: return null
        return folderConnection.createStorage(uri, contentResolver)
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

    companion object {
        val DEFAULT_GRANT_FLAGS: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
