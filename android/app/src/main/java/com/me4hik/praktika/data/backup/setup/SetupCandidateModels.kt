// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup candidate models
package com.me4hik.praktika.data.backup.setup

/**
 * Safe candidate classification for setup/replacement. No URI/payload in toString for diagnostics.
 */
enum class SetupCandidateClassification {
    EMPTY,
    VALID_EQUAL,
    VALID_DIFFERENT,
    INVALID_PLUS_MISSING,
    BOTH_UNTRUSTED,
    AMBIGUOUS,
    VALID_PLUS_UNREADABLE,
    UNREADABLE,
    UNSAFE_DATABASE,
    PROVIDER_FAILURE,
    PERMISSION_LOST,
    UNAVAILABLE,
}

data class SetupCandidateInspection(
    /** Opaque token; verifyWrite must present the same live-session token. */
    val token: Long,
    val classification: SetupCandidateClassification,
)

sealed interface SetupInspectResult {
    data class Ready(val inspection: SetupCandidateInspection) : SetupInspectResult

    data object PermissionLost : SetupInspectResult

    data object Unavailable : SetupInspectResult

    data object ProviderFailure : SetupInspectResult

    data object Busy : SetupInspectResult
}

sealed interface SetupVerifyWriteResult {
    data class Verified(val receipt: VerifiedSetupWriteReceipt) : SetupVerifyWriteResult

    data class CandidateStale(val inspection: SetupCandidateInspection) : SetupVerifyWriteResult

    data class Blocked(val classification: SetupCandidateClassification) : SetupVerifyWriteResult

    data object WriteFailed : SetupVerifyWriteResult

    data object ExportFailed : SetupVerifyWriteResult

    data object UnsafeDatabase : SetupVerifyWriteResult

    data object NoSession : SetupVerifyWriteResult

    data object Busy : SetupVerifyWriteResult
}

sealed interface SetupCommitResult {
    data class Success(
        val verifiedCreatedAtEpochMillis: Long,
        val postSwitchCatchupScheduled: Boolean,
    ) : SetupCommitResult

    data object ReceiptMissing : SetupCommitResult

    data object ReceiptStale : SetupCommitResult

    data object CommitFailed : SetupCommitResult

    data object InvalidUri : SetupCommitResult

    data object Busy : SetupCommitResult
}

sealed interface DisableAutomaticBackupResult {
    data object Success : DisableAutomaticBackupResult

    /** Auth cleared; permission cleanup may be incomplete. */
    data object DisabledWithPermissionCleanupWarning : DisableAutomaticBackupResult

    data object AuthClearFailed : DisableAutomaticBackupResult

    data object Busy : DisableAutomaticBackupResult
}

sealed interface SetupAbandonResult {
    data object Success : SetupAbandonResult

    data object Busy : SetupAbandonResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
