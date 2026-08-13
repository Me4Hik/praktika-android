// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

internal class RestoreAbortException(
    val result: BackupRestoreResult,
) : Exception(result.toString())

// 10.08.2026 Post-release fixes cursor by Me4Hik END
