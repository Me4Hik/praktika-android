// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.cycle.CycleResult

sealed interface NotificationSyncObservation {
    data object Invoked : NotificationSyncObservation
}

sealed interface BackupRestoreCoordinatorResult {
    data object NoValidBackup : BackupRestoreCoordinatorResult

    data class PreviewReady(
        val preview: BackupRestorePreview,
        val identity: BackupRestoreSelectedIdentity,
    ) : BackupRestoreCoordinatorResult

    data class PreviewStale(
        val previousIdentity: BackupRestoreSelectedIdentity,
        val currentIdentity: BackupRestoreSelectedIdentity?,
    ) : BackupRestoreCoordinatorResult

    data class TargetNotEligible(
        val eligibility: RestoreTargetEligibility,
    ) : BackupRestoreCoordinatorResult

    data class RestoreCoreFailure(
        val restoreResult: BackupRestoreResult,
    ) : BackupRestoreCoordinatorResult

    data class RestoreDataSuccess(
        val preview: BackupRestorePreview,
        val verification: PostRestoreVerificationResult,
        val reconcileResult: CycleResult?,
        val notificationSync: NotificationSyncObservation?,
    ) : BackupRestoreCoordinatorResult

    data class RestoreDataSuccessVerificationFailure(
        val preview: BackupRestorePreview,
        val verification: PostRestoreVerificationResult.Mismatch,
    ) : BackupRestoreCoordinatorResult

    data class RestoreDataSuccessRuntimeSyncWarning(
        val preview: BackupRestorePreview,
        val verification: PostRestoreVerificationResult,
        val dataRestoreCommitted: Boolean = true,
        val reconcileFailureClassName: String?,
        val reconcileFailureMessage: String? = null,
        val notificationSync: NotificationSyncObservation?,
    ) : BackupRestoreCoordinatorResult

    data class FullSuccess(
        val preview: BackupRestorePreview,
        val verification: PostRestoreVerificationResult,
        val reconcileResult: CycleResult,
        val notificationSync: NotificationSyncObservation,
    ) : BackupRestoreCoordinatorResult
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
