// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.BackupSlotId

sealed class BackupSlotWriteResult {
    data class Success(
        val slot: BackupSlotId,
        val sequence: Long,
        val checksum: String,
    ) : BackupSlotWriteResult()

    data object TooLarge : BackupSlotWriteResult()

    data object PermissionLost : BackupSlotWriteResult()

    data class SlotAmbiguous(
        val slot: BackupSlotId,
        val matchCount: Int,
    ) : BackupSlotWriteResult()

    data class DeleteFailed(
        val slot: BackupSlotId,
    ) : BackupSlotWriteResult()

    data class CreateFailed(
        val slot: BackupSlotId,
    ) : BackupSlotWriteResult()

    data class NameMismatch(
        val slot: BackupSlotId,
        val actualDisplayName: String,
    ) : BackupSlotWriteResult()

    data class OpenFailed(
        val slot: BackupSlotId,
    ) : BackupSlotWriteResult()

    data class WriteFailed(
        val slot: BackupSlotId,
        val exceptionClass: String,
    ) : BackupSlotWriteResult()

    data class PostWriteReadFailed(
        val slot: BackupSlotId,
    ) : BackupSlotWriteResult()

    data class PostWriteValidationFailed(
        val slot: BackupSlotId,
    ) : BackupSlotWriteResult()

    data class ExpectedIdentityMismatch(
        val slot: BackupSlotId,
        val field: String,
    ) : BackupSlotWriteResult()

    data class ProviderFailure(
        val slot: BackupSlotId,
        val exceptionClass: String,
    ) : BackupSlotWriteResult()

    data object Unavailable : BackupSlotWriteResult()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
