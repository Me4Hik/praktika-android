// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI models
package com.me4hik.praktika.ui.settings

import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.backup.setup.SetupCandidateClassification

enum class BackupPickerMode {
    SETUP,
    REPLACE,
    RECONNECT,
}

enum class BackupSetupIntent {
    SETUP,
    REPLACE,
}

sealed interface BackupSettingsOperation {
    data object Idle : BackupSettingsOperation
    data object AwaitingDisclosureSetup : BackupSettingsOperation
    data object AwaitingDisclosureReplace : BackupSettingsOperation
    data object AwaitingPicker : BackupSettingsOperation
    data object Inspecting : BackupSettingsOperation
    data object AwaitingCandidateConfirmation : BackupSettingsOperation
    data object Configuring : BackupSettingsOperation
    data object AwaitingCommitRetry : BackupSettingsOperation
    data object Reconnecting : BackupSettingsOperation
    data object BackingUpNow : BackupSettingsOperation
    data object Disabling : BackupSettingsOperation
    data object Abandoning : BackupSettingsOperation
    data object AwaitingDisableConfirmation : BackupSettingsOperation
    data object AwaitingReconnectDifferentFolder : BackupSettingsOperation
}

sealed interface BackupPendingConfirmation {
    data class CandidateWritable(
        val classification: SetupCandidateClassification,
    ) : BackupPendingConfirmation

    data class CandidateBlocked(
        val classification: SetupCandidateClassification,
    ) : BackupPendingConfirmation

    data object UnsafeDatabase : BackupPendingConfirmation

    data object CommitRetry : BackupPendingConfirmation

    data object Disable : BackupPendingConfirmation

    data object ReconnectDifferentFolder : BackupPendingConfirmation
}

data class BackupSettingsUiState(
    val operationalStatus: BackupSettingsOperationalState =
        BackupSettingsOperationalState.NotConfigured,
    val operation: BackupSettingsOperation = BackupSettingsOperation.Idle,
    val pendingConfirmation: BackupPendingConfirmation? = null,
    val statusUnavailable: Boolean = false,
) {
    val isBusy: Boolean
        get() = operation != BackupSettingsOperation.Idle &&
            operation != BackupSettingsOperation.AwaitingDisclosureSetup &&
            operation != BackupSettingsOperation.AwaitingDisclosureReplace &&
            operation != BackupSettingsOperation.AwaitingCandidateConfirmation &&
            operation != BackupSettingsOperation.AwaitingCommitRetry &&
            operation != BackupSettingsOperation.AwaitingDisableConfirmation &&
            operation != BackupSettingsOperation.AwaitingReconnectDifferentFolder &&
            operation != BackupSettingsOperation.AwaitingPicker

    val backupActionsEnabled: Boolean
        get() = operation == BackupSettingsOperation.Idle
}

sealed interface SettingsBackupEffect {
    data class LaunchFolderPicker(
        val mode: BackupPickerMode,
    ) : SettingsBackupEffect
}

fun SetupCandidateClassification.isWritableCandidate(): Boolean {
    return when (this) {
        SetupCandidateClassification.EMPTY,
        SetupCandidateClassification.VALID_EQUAL,
        SetupCandidateClassification.VALID_DIFFERENT,
        SetupCandidateClassification.INVALID_PLUS_MISSING,
        -> true
        else -> false
    }
}

fun SetupCandidateClassification.isBlockedCandidate(): Boolean {
    return when (this) {
        SetupCandidateClassification.BOTH_UNTRUSTED,
        SetupCandidateClassification.AMBIGUOUS,
        SetupCandidateClassification.VALID_PLUS_UNREADABLE,
        SetupCandidateClassification.UNREADABLE,
        SetupCandidateClassification.PROVIDER_FAILURE,
        SetupCandidateClassification.PERMISSION_LOST,
        SetupCandidateClassification.UNAVAILABLE,
        -> true
        SetupCandidateClassification.UNSAFE_DATABASE -> false
        else -> !isWritableCandidate()
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
