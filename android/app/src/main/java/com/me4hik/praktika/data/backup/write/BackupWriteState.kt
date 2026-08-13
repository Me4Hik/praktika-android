// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 backup write state
package com.me4hik.praktika.data.backup.write

/**
 * Internal persistence snapshot for automatic backup authorization + status.
 * Raw URI strings are allowed here only; never expose them from UI/diagnostics models.
 */
data class BackupWriteState(
    val treeUriHint: String?,
    val authorizedTreeUri: String?,
    val lastSuccessfulBackupAtEpochMillis: Long?,
    val lastFailureCategory: BackupFailureStatus?,
    val needsReconnect: Boolean,
)
// 10.08.2026 Post-release fixes cursor by Me4Hik END
