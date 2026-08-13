// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.metadata

data class BackupSnapshotMetadataContext(
    val createdAtEpochMillis: Long,
    val sourceAppVersionCode: Int,
    val sourceAppVersionName: String,
)
// 10.08.2026 Post-release fixes cursor by Me4Hik END
