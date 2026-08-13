// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2A coordinator factory
package com.me4hik.praktika.data.backup.write

import com.me4hik.praktika.data.backup.storage.BackupStorageProvider

/**
 * Creates a Stage 6.0 coordinator bound to a specific storage for one attempt.
 * Storage is FIXED for the returned instance lifetime — do not reuse across auth URI switches.
 */
fun interface ProductionBackupCoordinatorFactory {
    fun create(storage: BackupStorageProvider): ProductionBackupCoordinator
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
