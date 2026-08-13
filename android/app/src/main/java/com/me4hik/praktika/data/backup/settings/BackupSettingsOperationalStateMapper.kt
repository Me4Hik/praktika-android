// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 settings status mapper
package com.me4hik.praktika.data.backup.settings

import com.me4hik.praktika.data.backup.write.BackupFailureStatus
import com.me4hik.praktika.data.backup.write.BackupTreeUriSanitizer
import com.me4hik.praktika.data.backup.write.BackupWriteState

/**
 * Maps durable [BackupWriteState] to URI-free Settings operational state.
 * Does not expose hint/restore internals from [com.me4hik.praktika.data.backup.write.BackupOperationalStateMapper].
 */
object BackupSettingsOperationalStateMapper {
    fun map(state: BackupWriteState): BackupSettingsOperationalState {
        val authorized = state.authorizedTreeUri
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(authorized)) {
            return BackupSettingsOperationalState.NotConfigured
        }
        val lastSuccess = state.lastSuccessfulBackupAtEpochMillis
        if (state.needsReconnect || state.lastFailureCategory == BackupFailureStatus.PERMISSION_LOST) {
            return BackupSettingsOperationalState.NeedsReconnect(lastSuccess)
        }
        return when (val failure = state.lastFailureCategory) {
            null -> {
                if (lastSuccess != null) {
                    BackupSettingsOperationalState.Healthy(lastSuccess)
                } else {
                    BackupSettingsOperationalState.ConfiguredNoSuccessCache
                }
            }

            BackupFailureStatus.AMBIGUOUS_SLOT,
            BackupFailureStatus.UNTRUSTED_ARTIFACTS,
            BackupFailureStatus.SEQUENCE_EXHAUSTED,
            -> BackupSettingsOperationalState.NeedsAttention(lastSuccess)

            BackupFailureStatus.UNSAFE_DATABASE ->
                BackupSettingsOperationalState.DataProblem(lastSuccess)

            BackupFailureStatus.WRITE_FAILED,
            BackupFailureStatus.EXPORT_FAILED,
            BackupFailureStatus.STORAGE_UNAVAILABLE,
            BackupFailureStatus.STORAGE_READ_FAILED,
            BackupFailureStatus.STORAGE_ACCESS_UNRELIABLE,
            BackupFailureStatus.POST_WRITE_VALIDATION_FAILED,
            BackupFailureStatus.UNKNOWN,
            -> BackupSettingsOperationalState.TransientFailure(lastSuccess)

            BackupFailureStatus.PERMISSION_LOST ->
                BackupSettingsOperationalState.NeedsReconnect(lastSuccess)
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
