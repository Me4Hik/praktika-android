// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - structured backup export results
package com.me4hik.praktika.data.backup.export

import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload

sealed interface BackupExportResult {
    data class Success(
        val payload: PraktikaBackupPayload,
    ) : BackupExportResult

    data class DatabaseUnsafe(
        val reason: BackupDatabaseUnsafeReason,
    ) : BackupExportResult

    data class ReadFailure(
        val exceptionClass: String,
    ) : BackupExportResult
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
