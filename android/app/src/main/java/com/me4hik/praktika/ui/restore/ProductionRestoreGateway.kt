// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 restore gateway seam
package com.me4hik.praktika.ui.restore

import android.net.Uri
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider

interface ProductionRestoreGateway {
    suspend fun checkTargetEligibility(): RestoreTargetEligibility

    suspend fun readTreeUriHint(): String?

    suspend fun clearTreeUri()

    suspend fun readActiveTreeUri(): String?

    suspend fun connectFolder(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreConnectOutcome

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A composite candidate IO
    suspend fun connectAndInspectCandidate(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreCandidateIoResult
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    suspend fun commitActiveBackupFolder(uri: Uri): Boolean

    suspend fun releaseRejectedTransientGrant(
        uri: Uri,
        grantFlags: Int,
    )

    suspend fun inspectLatest(
        storage: BackupStorageProvider,
    ): BackupRestoreCoordinatorResult

    suspend fun executeRestore(
        storage: BackupStorageProvider,
        previewIdentity: BackupRestoreSelectedIdentity,
        preview: BackupRestorePreview?,
    ): BackupRestoreCoordinatorResult
}

sealed interface ProductionRestoreConnectOutcome {
    data class Success(
        val storage: BackupStorageProvider,
        val treeUri: Uri,
    ) : ProductionRestoreConnectOutcome

    data object PermissionLost : ProductionRestoreConnectOutcome

    data object ReadFailure : ProductionRestoreConnectOutcome

    data object WriteFailure : ProductionRestoreConnectOutcome

    data object Unexpected : ProductionRestoreConnectOutcome
}

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A candidate IO result
sealed interface ProductionRestoreCandidateIoResult {
    data class Connected(
        val storage: BackupStorageProvider,
        val treeUri: Uri,
        val inspect: BackupRestoreCoordinatorResult,
    ) : ProductionRestoreCandidateIoResult

    data object PermissionLost : ProductionRestoreCandidateIoResult

    data object ReadFailure : ProductionRestoreCandidateIoResult

    data object WriteFailure : ProductionRestoreCandidateIoResult

    data object Unexpected : ProductionRestoreCandidateIoResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
