// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 settings operational state
package com.me4hik.praktika.data.backup.settings

/**
 * URI-free durable backup status for future Settings UI.
 * No hint, URI, failure enum, or storage internals.
 */
sealed interface BackupSettingsOperationalState {
    data object NotConfigured : BackupSettingsOperationalState

    data class Healthy(
        val lastSuccessAtEpochMillis: Long,
    ) : BackupSettingsOperationalState

    data object ConfiguredNoSuccessCache : BackupSettingsOperationalState

    data class NeedsReconnect(
        val lastSuccessAtEpochMillis: Long?,
    ) : BackupSettingsOperationalState

    data class TransientFailure(
        val lastSuccessAtEpochMillis: Long?,
    ) : BackupSettingsOperationalState

    data class NeedsAttention(
        val lastSuccessAtEpochMillis: Long?,
    ) : BackupSettingsOperationalState

    data class DataProblem(
        val lastSuccessAtEpochMillis: Long?,
    ) : BackupSettingsOperationalState
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
