// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening
package com.me4hik.praktika.data.backup.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class BackupFolderAccessValidator(
    private val contentResolver: ContentResolver,
    private val treeQuery: SafTreeDocumentIo = SafTreeDocumentIo(contentResolver),
) : BackupTreeAccessValidator {
    override fun validate(treeUri: Uri): BackupFolderAccessState {
        if (!isStructurallyUsable(treeUri)) {
            return BackupFolderAccessState.Unavailable
        }
        if (!hasPersistedReadWriteGrant(treeUri)) {
            return BackupFolderAccessState.PermissionLost
        }
        return try {
            treeQuery.queryChildren(treeUri)
            BackupFolderAccessState.Connected(treeUri)
        } catch (securityException: SecurityException) {
            BackupFolderAccessState.PermissionLost
        } catch (exception: Exception) {
            BackupFolderAccessState.ProviderError(exception.javaClass.name)
        }
    }

    private fun isStructurallyUsable(treeUri: Uri): Boolean {
        if (treeUri.scheme != ContentResolver.SCHEME_CONTENT) {
            return false
        }
        return try {
            DocumentsContract.getTreeDocumentId(treeUri)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun hasPersistedReadWriteGrant(treeUri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri &&
                permission.isReadPermission &&
                permission.isWritePermission
        }
    }
}

fun interface PersistableTreePermissionGrant {
    fun takePersistableUriPermission(treeUri: Uri, flags: Int)
}

fun interface PersistableTreePermissionRelease {
    fun releasePersistableUriPermission(treeUri: Uri, flags: Int)
}

class BackupFolderConnection(
    private val treeConfigRepository: BackupTreeConfigRepository,
    private val accessValidator: BackupTreeAccessValidator,
    private val permissionGrant: PersistableTreePermissionGrant,
    private val permissionRelease: PersistableTreePermissionRelease,
) {
    constructor(
        contentResolver: ContentResolver,
        treeConfigRepository: BackupTreeConfigRepository,
        accessValidator: BackupTreeAccessValidator = BackupFolderAccessValidator(contentResolver),
    ) : this(
        treeConfigRepository = treeConfigRepository,
        accessValidator = accessValidator,
        permissionGrant = PersistableTreePermissionGrant { treeUri, flags ->
            contentResolver.takePersistableUriPermission(treeUri, flags)
        },
        permissionRelease = PersistableTreePermissionRelease { treeUri, flags ->
            contentResolver.releasePersistableUriPermission(treeUri, flags)
        },
    )

    suspend fun connect(
        treeUri: Uri,
        grantedFlags: Int,
    ): BackupFolderConnectionResult = establishConnection(
        treeUri = treeUri,
        grantedFlags = grantedFlags,
        persistHint = true,
    )

    suspend fun connectTransient(
        treeUri: Uri,
        grantedFlags: Int,
    ): BackupFolderConnectionResult = establishConnection(
        treeUri = treeUri,
        grantedFlags = grantedFlags,
        persistHint = false,
    )

    fun releasePersistedPermission(treeUri: Uri, grantedFlags: Int) {
        val releaseFlags = grantedFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        if (releaseFlags == 0) {
            return
        }
        try {
            permissionRelease.releasePersistableUriPermission(treeUri, releaseFlags)
        } catch (_: SecurityException) {
            // Grant may already be absent; rejected-folder cleanup is best-effort.
        } catch (_: Exception) {
            // Best-effort cleanup for transient candidates.
        }
    }

    private suspend fun establishConnection(
        treeUri: Uri,
        grantedFlags: Int,
        persistHint: Boolean,
    ): BackupFolderConnectionResult = withContext(Dispatchers.IO) {
        val takeFlags = grantedFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        if (takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 ||
            takeFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0
        ) {
            return@withContext BackupFolderConnectionResult.PermissionLost
        }

        try {
            permissionGrant.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: SecurityException) {
            return@withContext BackupFolderConnectionResult.PermissionLost
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            return@withContext BackupFolderConnectionResult.ProviderError(exception.javaClass.name)
        }

        when (val access = accessValidator.validate(treeUri)) {
            is BackupFolderAccessState.Connected -> {
                if (persistHint) {
                    treeConfigRepository.saveTreeUri(treeUri.toString())
                }
                BackupFolderConnectionResult.Success(access.treeUri)
            }
            BackupFolderAccessState.PermissionLost ->
                BackupFolderConnectionResult.PermissionLost
            BackupFolderAccessState.Unavailable ->
                BackupFolderConnectionResult.Unavailable
            is BackupFolderAccessState.ProviderError ->
                BackupFolderConnectionResult.ProviderError(access.exceptionClass)
        }
    }

    fun validateAccess(treeUri: Uri): BackupFolderAccessState {
        return accessValidator.validate(treeUri)
    }

    fun createStorage(treeUri: Uri, contentResolver: ContentResolver): LocalSafBackupStorage {
        val treeQuery = SafTreeDocumentIo(contentResolver)
        return LocalSafBackupStorage(
            treeUri = treeUri,
            treeQuery = treeQuery,
            accessValidator = BackupFolderAccessValidator(contentResolver, treeQuery),
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
