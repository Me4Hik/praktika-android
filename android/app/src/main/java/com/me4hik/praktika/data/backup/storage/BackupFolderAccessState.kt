// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import android.net.Uri

sealed class BackupFolderAccessState {
    data class Connected(val treeUri: Uri) : BackupFolderAccessState()

    data object PermissionLost : BackupFolderAccessState()

    data object Unavailable : BackupFolderAccessState()

    data class ProviderError(val exceptionClass: String) : BackupFolderAccessState()
}

sealed class BackupFolderConnectionResult {
    data class Success(val treeUri: Uri) : BackupFolderConnectionResult()

    data object PermissionLost : BackupFolderConnectionResult()

    data object Unavailable : BackupFolderConnectionResult()

    data class ProviderError(val exceptionClass: String) : BackupFolderConnectionResult()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
