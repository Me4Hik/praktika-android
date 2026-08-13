// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.metadata

class BackupSnapshotMetadataFactory(
    private val clock: BackupClock,
    private val appMetadataProvider: BackupAppMetadataProvider,
) {
    fun capture(): BackupSnapshotMetadataContext {
        val createdAtEpochMillis = clock.nowEpochMillis()
        val appMetadata = appMetadataProvider.current()
        return BackupSnapshotMetadataContext(
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = appMetadata.versionCode,
            sourceAppVersionName = appMetadata.versionName,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
