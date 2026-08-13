// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - authoritative write-state bind for acceptance harness
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.write.BackupTreeUriSanitizer
import com.me4hik.praktika.data.backup.write.BackupWriteState
import com.me4hik.praktika.data.backup.write.BackupWriteStateRepository

/**
 * Accelerated-only hold of the single [BackupWriteStateRepository] instance created by
 * [com.me4hik.praktika.runtime.RuntimeFactory]. Never constructs a second DataStore.
 */
object AcceleratedBackupWriteStateAccess {
    @Volatile
    private var repository: BackupWriteStateRepository? = null

    fun bind(writeStateRepository: BackupWriteStateRepository) {
        repository = writeStateRepository
    }

    fun clearForTests() {
        repository = null
    }

    fun isBound(): Boolean = repository != null

    suspend fun snapshot(): BackupWriteState? = repository?.snapshot()

    suspend fun currentAuthorizedTreeUri(): String? {
        val raw = snapshot()?.authorizedTreeUri
        return raw?.takeIf { BackupTreeUriSanitizer.isUsableTreeUriString(it) }
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
