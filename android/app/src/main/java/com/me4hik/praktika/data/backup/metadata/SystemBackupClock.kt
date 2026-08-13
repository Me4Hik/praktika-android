// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.metadata

object SystemBackupClock : BackupClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
