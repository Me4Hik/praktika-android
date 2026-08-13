// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A prepare execute restore result
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

sealed interface PrepareExecuteRestoreResult {
    data class Ready(
        val envelope: PraktikaBackupEnvelope,
        val preview: BackupRestorePreview,
    ) : PrepareExecuteRestoreResult

    data class Terminal(
        val result: BackupRestoreCoordinatorResult,
    ) : PrepareExecuteRestoreResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
