// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage tests
package com.me4hik.praktika.data.backup.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryBackupTreeConfigRepository : BackupTreeConfigRepository {
    private val hint = MutableStateFlow<String?>(null)

    override val treeUriHint: Flow<String?> = hint

    override suspend fun saveTreeUri(uriString: String) {
        hint.value = uriString
    }

    override suspend fun clearTreeUri() {
        hint.value = null
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
