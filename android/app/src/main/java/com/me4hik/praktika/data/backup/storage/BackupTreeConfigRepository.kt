// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import kotlinx.coroutines.flow.Flow

interface BackupTreeConfigRepository {
    val treeUriHint: Flow<String?>

    suspend fun saveTreeUri(uriString: String)

    suspend fun clearTreeUri()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
