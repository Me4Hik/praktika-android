// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A verified setup write receipt
package com.me4hik.praktika.data.backup.setup

import com.me4hik.praktika.data.backup.model.BackupSlotId

/**
 * In-memory proof that this process's setup session physically wrote and re-verified
 * a backup into the candidate folder. Never persisted; never exposed to UI/logs with URI.
 */
data class VerifiedSetupWriteReceipt internal constructor(
    internal val candidateUriString: String,
    val slot: BackupSlotId,
    val sequence: Long,
    val createdAtEpochMillis: Long,
    val checksumSha256: String,
) {
    internal fun matchesPhysical(
        candidateUriString: String,
        slot: BackupSlotId,
        sequence: Long,
        createdAtEpochMillis: Long,
        checksumSha256: String,
    ): Boolean {
        return this.candidateUriString == candidateUriString &&
            this.slot == slot &&
            this.sequence == sequence &&
            this.createdAtEpochMillis == createdAtEpochMillis &&
            this.checksumSha256 == checksumSha256
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
