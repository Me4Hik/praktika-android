// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 production restore models
package com.me4hik.praktika.ui.restore

import android.net.Uri

sealed interface ProductionRestoreUiState {
    data object Hidden : ProductionRestoreUiState

    data object Idle : ProductionRestoreUiState

    data object CheckingEligibility : ProductionRestoreUiState

    data class Blocked(
        val reason: ProductionRestoreTargetBlockReason,
    ) : ProductionRestoreUiState

    data object ChoosingFolder : ProductionRestoreUiState

    data object Connecting : ProductionRestoreUiState

    data object Inspecting : ProductionRestoreUiState

    data class PreviewReady(
        val preview: ProductionRestorePreviewUiModel,
        val staleNotice: Boolean = false,
    ) : ProductionRestoreUiState

    data class Restoring(
        val preview: ProductionRestorePreviewUiModel,
    ) : ProductionRestoreUiState

    data class RestoreSuccess(
        val preview: ProductionRestorePreviewUiModel,
    ) : ProductionRestoreUiState

    data class RuntimeSyncWarning(
        val preview: ProductionRestorePreviewUiModel,
    ) : ProductionRestoreUiState

    data class Error(
        val error: ProductionRestoreError,
    ) : ProductionRestoreUiState
}

enum class ProductionRestoreTargetBlockReason {
    TargetNotEmpty,
    TargetUnsafe,
}

sealed interface ProductionRestoreError {
    data object NoBackupFound : ProductionRestoreError

    data object InvalidBackup : ProductionRestoreError

    data object IncompatibleBackup : ProductionRestoreError

    data object PermissionLost : ProductionRestoreError

    data object ReadFailure : ProductionRestoreError

    data object UnsafeTarget : ProductionRestoreError

    data object TargetNotEmpty : ProductionRestoreError

    data object ActiveFolderSaveFailure : ProductionRestoreError

    data object RestoreWriteFailure : ProductionRestoreError

    data object Unexpected : ProductionRestoreError
}

data class ProductionRestorePreviewUiModel(
    val createdAtText: String,
    val answerCount: Int,
    val completedCount: Int,
    val practiceStarted: Boolean,
    val deletedTextCount: Int,
    val scheduleWillBeRestored: Boolean = true,
)

data class FolderPickerResult(
    val uri: Uri,
    val grantFlags: Int,
)

sealed interface ProductionRestoreEffect {
    data class LaunchFolderPicker(
        val initialUriHint: String?,
    ) : ProductionRestoreEffect

    data class RestoreCommitted(
        val practiceStarted: Boolean,
    ) : ProductionRestoreEffect
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
