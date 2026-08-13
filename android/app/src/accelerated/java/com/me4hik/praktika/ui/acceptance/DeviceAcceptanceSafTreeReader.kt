// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - read-only SAF tree slot reader
package com.me4hik.praktika.ui.acceptance

import android.content.ContentResolver
import android.net.Uri
import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessState
import com.me4hik.praktika.data.backup.storage.BackupFolderAccessValidator
import com.me4hik.praktika.data.backup.storage.BoundedBackupReader
import com.me4hik.praktika.data.backup.storage.SafTreeDocumentIo
import com.me4hik.praktika.data.backup.storage.SlotDocumentDiscovery
import com.me4hik.praktika.data.backup.storage.SlotDocumentMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeviceAcceptanceFolderReadResult {
    data class Ok(
        val slotA: DeviceAcceptanceSlotBytes,
        val slotB: DeviceAcceptanceSlotBytes,
    ) : DeviceAcceptanceFolderReadResult()

    data object AccessUnavailable : DeviceAcceptanceFolderReadResult()

    data object PermissionDenied : DeviceAcceptanceFolderReadResult()

    data object ProviderFailure : DeviceAcceptanceFolderReadResult()
}

fun interface DeviceAcceptanceTreeReader {
    suspend fun readAuthorizedOrStashed(
        uriString: String,
        requirePersistedGrant: Boolean,
    ): DeviceAcceptanceFolderReadResult
}

/**
 * Read-only SAF inspector. Never create/delete/write documents; never persist grants.
 */
class DeviceAcceptanceSafTreeReader(
    private val contentResolver: ContentResolver,
    private val treeIo: SafTreeDocumentIo = SafTreeDocumentIo(contentResolver),
    private val accessValidator: BackupFolderAccessValidator =
        BackupFolderAccessValidator(contentResolver, treeIo),
) : DeviceAcceptanceTreeReader {
    override suspend fun readAuthorizedOrStashed(
        uriString: String,
        requirePersistedGrant: Boolean,
    ): DeviceAcceptanceFolderReadResult = withContext(Dispatchers.IO) {
        val uri = parseContentUri(uriString) ?: return@withContext DeviceAcceptanceFolderReadResult.AccessUnavailable
        if (requirePersistedGrant) {
            when (val access = accessValidator.validate(uri)) {
                is BackupFolderAccessState.Connected -> readChildren(access.treeUri)
                BackupFolderAccessState.PermissionLost ->
                    DeviceAcceptanceFolderReadResult.PermissionDenied
                BackupFolderAccessState.Unavailable ->
                    DeviceAcceptanceFolderReadResult.AccessUnavailable
                is BackupFolderAccessState.ProviderError ->
                    DeviceAcceptanceFolderReadResult.ProviderFailure
            }
        } else {
            // Temporary ActivityResult grant: skip persisted-grant gate; try query/read directly.
            try {
                readChildren(uri)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: SecurityException) {
                DeviceAcceptanceFolderReadResult.PermissionDenied
            } catch (_: Exception) {
                DeviceAcceptanceFolderReadResult.ProviderFailure
            }
        }
    }

    private fun readChildren(treeUri: Uri): DeviceAcceptanceFolderReadResult {
        return try {
            val children = treeIo.queryChildren(treeUri)
            val matches = SlotDocumentDiscovery.discoverAll(children)
            DeviceAcceptanceFolderReadResult.Ok(
                slotA = readMatch(treeUri, BackupSlotId.A, matches.getValue(BackupSlotId.A)),
                slotB = readMatch(treeUri, BackupSlotId.B, matches.getValue(BackupSlotId.B)),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: SecurityException) {
            DeviceAcceptanceFolderReadResult.PermissionDenied
        } catch (_: Exception) {
            DeviceAcceptanceFolderReadResult.ProviderFailure
        }
    }

    private fun readMatch(
        treeUri: Uri,
        slot: BackupSlotId,
        match: SlotDocumentMatch,
    ): DeviceAcceptanceSlotBytes {
        return when (match) {
            SlotDocumentMatch.Missing -> DeviceAcceptanceSlotBytes.Missing
            is SlotDocumentMatch.Ambiguous -> DeviceAcceptanceSlotBytes.Ambiguous(match.matchCount)
            is SlotDocumentMatch.Located -> {
                val size = match.document.sizeBytes
                if (size != null && size > BackupConstants.MAX_BACKUP_BYTES) {
                    return DeviceAcceptanceSlotBytes.TooLarge
                }
                val documentUri = treeIo.buildDocumentUri(treeUri, match.document.documentId)
                when (val read = treeIo.readDocumentBounded(documentUri)) {
                    BoundedBackupReader.ReadResult.TooLarge -> DeviceAcceptanceSlotBytes.TooLarge
                    is BoundedBackupReader.ReadResult.Failed ->
                        DeviceAcceptanceSlotBytes.Unreadable(DeviceAcceptanceDecodeStatus.UNREADABLE)
                    is BoundedBackupReader.ReadResult.Success ->
                        DeviceAcceptanceSlotBytes.Readable(read.bytes)
                }
            }
        }
    }

    private fun parseContentUri(uriString: String): Uri? {
        if (uriString.isBlank()) return null
        val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return uri
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
