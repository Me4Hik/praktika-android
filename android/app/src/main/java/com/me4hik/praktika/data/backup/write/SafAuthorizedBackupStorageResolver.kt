// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A SAF authorized storage resolver
package com.me4hik.praktika.data.backup.write

import android.content.ContentResolver
import android.net.Uri
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessValidator
import com.me4hik.praktika.data.backup.storage.BackupTreeAccessValidator
import com.me4hik.praktika.data.backup.storage.LocalSafBackupStorage
import com.me4hik.praktika.data.backup.storage.SafTreeDocumentIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production resolver: validates existing persisted READ+WRITE grant + tree usability,
 * then constructs [LocalSafBackupStorage]. Never calls takePersistableUriPermission or saveTreeUri.
 */
class SafAuthorizedBackupStorageResolver(
    private val contentResolver: ContentResolver,
    private val accessValidator: BackupTreeAccessValidator =
        BackupFolderAccessValidator(contentResolver),
) : AuthorizedBackupStorageResolver {
    override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse(authorizedUriString)
            when (val access = accessValidator.validate(uri)) {
                is BackupFolderAccessState.Connected -> {
                    val treeQuery = SafTreeDocumentIo(contentResolver)
                    AuthorizedStorageResolveResult.Ready(
                        LocalSafBackupStorage(
                            treeUri = access.treeUri,
                            treeQuery = treeQuery,
                            accessValidator = BackupFolderAccessValidator(contentResolver, treeQuery),
                        ),
                    )
                }

                BackupFolderAccessState.PermissionLost ->
                    AuthorizedStorageResolveResult.PermissionLost

                BackupFolderAccessState.Unavailable ->
                    AuthorizedStorageResolveResult.Unavailable

                is BackupFolderAccessState.ProviderError ->
                    AuthorizedStorageResolveResult.ProviderError
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
