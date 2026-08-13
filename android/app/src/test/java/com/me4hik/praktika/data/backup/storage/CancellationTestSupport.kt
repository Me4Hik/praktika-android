// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening tests
package com.me4hik.praktika.data.backup.storage

import android.net.Uri

internal fun testTreeUri(value: String = "content://test/tree"): Uri = Uri.parse(value)

internal class ConnectedAccessValidator(
    private val connectedTreeUri: Uri,
) : BackupTreeAccessValidator {
    override fun validate(treeUri: Uri): BackupFolderAccessState {
        return BackupFolderAccessState.Connected(connectedTreeUri)
    }
}

internal class PermissionLostAccessValidator : BackupTreeAccessValidator {
    override fun validate(treeUri: Uri): BackupFolderAccessState {
        return BackupFolderAccessState.PermissionLost
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
