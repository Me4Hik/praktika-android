// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A backup IO session gate
package com.me4hik.praktika.data.backup.write

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-scoped exclusive gate for backup (and restore) physical IO.
 * Stage 6.2B2-A: shared by AuthorizedBackupService and DefaultProductionRestoreGateway.
 */
class BackupIoSessionGate {
    private val mutex = Mutex()

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A gate depth seams
    @Volatile
    internal var exclusiveDepthForTests: Int = 0
        private set

    @Volatile
    internal var maxExclusiveDepthForTests: Int = 0
        private set

    @Volatile
    internal var enterCountForTests: Int = 0
        private set
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    suspend fun <T> withExclusive(block: suspend () -> T): T = mutex.withLock {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A gate depth seams
        exclusiveDepthForTests++
        enterCountForTests++
        maxExclusiveDepthForTests = maxOf(maxExclusiveDepthForTests, exclusiveDepthForTests)
        try {
            block()
        } finally {
            exclusiveDepthForTests--
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
