// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

fun interface BackupRestoreEngine {
    suspend fun restore(envelope: PraktikaBackupEnvelope): BackupRestoreResult
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
