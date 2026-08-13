package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility

sealed class RestoreProofUiState {
    data object Disconnected : RestoreProofUiState()

    data object Connecting : RestoreProofUiState()

    data class Connected(
        val providerAuthority: String?,
        val readGrant: Boolean,
        val writeGrant: Boolean,
        val restoredFromHint: Boolean = false,
    ) : RestoreProofUiState()

    data object Inspecting : RestoreProofUiState()

    data class ReadyForPrepare(
        val aSummary: RestoreSlotSummary,
        val bSummary: RestoreSlotSummary,
    ) : RestoreProofUiState()

    data object Preparing : RestoreProofUiState()

    data class PrepareDone(
        val aSummary: RestoreSlotSummary,
        val bSummary: RestoreSlotSummary,
        val latestSlot: BackupSlotId,
    ) : RestoreProofUiState()

    data class ReadyForRestore(
        val aSummary: RestoreSlotSummary,
        val bSummary: RestoreSlotSummary,
        val latestSlot: BackupSlotId?,
        val targetEligibility: RestoreTargetEligibility?,
    ) : RestoreProofUiState()

    data class PreviewReady(
        val preview: BackupRestorePreview,
        val aSummary: RestoreSlotSummary,
        val bSummary: RestoreSlotSummary,
        val targetEligibility: RestoreTargetEligibility,
    ) : RestoreProofUiState()

    data object Restoring : RestoreProofUiState()

    data class RoundtripVerified(
        val preview: BackupRestorePreview,
        val evidenceSummary: String,
    ) : RestoreProofUiState()

    data class RuntimeSynced(
        val preview: BackupRestorePreview,
        val reconcileSummary: String,
        val verificationEvidence: String? = null,
    ) : RestoreProofUiState()

    data class RuntimeSyncFailed(
        val preview: BackupRestorePreview,
        val verificationEvidence: String,
        val reconcileFailureClassName: String,
        val reconcileFailureMessage: String?,
    ) : RestoreProofUiState()

    data class IdempotenceResult(
        val outcome: String,
    ) : RestoreProofUiState()

    data class Failure(val code: String) : RestoreProofUiState()
}

sealed class RestoreProofUiEvent {
    data object RequestPicker : RestoreProofUiEvent()
}
