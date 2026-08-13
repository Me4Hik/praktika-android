package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility

object RestoreProofActionGate {
    fun canPrepare(uiState: RestoreProofUiState): Boolean {
        return uiState is RestoreProofUiState.ReadyForPrepare
    }

    fun canPreview(uiState: RestoreProofUiState): Boolean {
        return uiState is RestoreProofUiState.ReadyForRestore && uiState.latestSlot != null
    }

    fun canRestore(uiState: RestoreProofUiState): Boolean {
        return uiState is RestoreProofUiState.PreviewReady &&
            uiState.targetEligibility is RestoreTargetEligibility.RestoreAvailable
    }

    fun canRetryRestore(uiState: RestoreProofUiState): Boolean {
        return uiState is RestoreProofUiState.RuntimeSynced ||
            uiState is RestoreProofUiState.RuntimeSyncFailed ||
            uiState is RestoreProofUiState.IdempotenceResult
    }
}
