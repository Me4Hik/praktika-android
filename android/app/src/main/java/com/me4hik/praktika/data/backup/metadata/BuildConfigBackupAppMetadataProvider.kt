// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.metadata

import com.me4hik.praktika.BuildConfig

class BuildConfigBackupAppMetadataProvider : BackupAppMetadataProvider {
    override fun current(): BackupSourceAppMetadata {
        return BackupSourceAppMetadata(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
