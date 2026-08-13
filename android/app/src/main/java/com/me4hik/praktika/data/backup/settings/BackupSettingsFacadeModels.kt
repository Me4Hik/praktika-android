// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 facade result models
package com.me4hik.praktika.data.backup.settings

import com.me4hik.praktika.data.backup.setup.SetupCandidateClassification

/** UI-neutral setup inspection; no token/URI. */
sealed interface BackupSettingsSetupInspectResult {
    data class Ready(
        val classification: SetupCandidateClassification,
    ) : BackupSettingsSetupInspectResult

    data object PermissionLost : BackupSettingsSetupInspectResult

    data object Unavailable : BackupSettingsSetupInspectResult

    data object ProviderFailure : BackupSettingsSetupInspectResult

    data object Busy : BackupSettingsSetupInspectResult
}

/** UI-neutral verify; no receipt/token/URI. */
sealed interface BackupSettingsSetupVerifyResult {
    data object Verified : BackupSettingsSetupVerifyResult

    data class CandidateStale(
        val classification: SetupCandidateClassification,
    ) : BackupSettingsSetupVerifyResult

    data class Blocked(
        val classification: SetupCandidateClassification,
    ) : BackupSettingsSetupVerifyResult

    data object WriteFailed : BackupSettingsSetupVerifyResult

    data object ExportFailed : BackupSettingsSetupVerifyResult

    data object UnsafeDatabase : BackupSettingsSetupVerifyResult

    data object NoSession : BackupSettingsSetupVerifyResult

    data object Busy : BackupSettingsSetupVerifyResult
}

/** UI-neutral commit; timestamp only on success. */
sealed interface BackupSettingsSetupCommitResult {
    data class Success(
        val verifiedCreatedAtEpochMillis: Long,
        val postSwitchCatchupScheduled: Boolean,
    ) : BackupSettingsSetupCommitResult

    data object ReceiptMissing : BackupSettingsSetupCommitResult

    data object ReceiptStale : BackupSettingsSetupCommitResult

    data object CommitFailed : BackupSettingsSetupCommitResult

    data object InvalidConfiguration : BackupSettingsSetupCommitResult

    data object Busy : BackupSettingsSetupCommitResult
}

sealed interface BackupSettingsAbandonResult {
    data object Success : BackupSettingsAbandonResult

    data object Busy : BackupSettingsAbandonResult
}

sealed interface BackupReconnectResult {
    data object Success : BackupReconnectResult

    data object DifferentFolder : BackupReconnectResult

    data object NotConfigured : BackupReconnectResult

    data object NotReconnectRequired : BackupReconnectResult

    data object PermissionFailure : BackupReconnectResult

    data object ValidationFailure : BackupReconnectResult

    data object StaleConfiguration : BackupReconnectResult
}

enum class BackupNowPhysicalResult {
    WRITTEN,
    NO_CHANGE,
}

sealed interface BackupNowResult {
    data object Written : BackupNowResult

    data object NoChange : BackupNowResult

    data object NotConfigured : BackupNowResult

    data object NeedsReconnect : BackupNowResult

    data object TransientFailure : BackupNowResult

    data object NeedsAttention : BackupNowResult

    data object DataProblem : BackupNowResult

    data class StatusTrackingIncomplete(
        val physicalResult: BackupNowPhysicalResult,
    ) : BackupNowResult

    data object TemporarilyUnavailable : BackupNowResult
}

sealed interface BackupSettingsDisableResult {
    data object Success : BackupSettingsDisableResult

    data object PermissionCleanupWarning : BackupSettingsDisableResult

    data object Failed : BackupSettingsDisableResult

    data object Busy : BackupSettingsDisableResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
