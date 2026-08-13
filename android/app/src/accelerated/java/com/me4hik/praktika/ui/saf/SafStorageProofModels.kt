package com.me4hik.praktika.ui.saf

import com.me4hik.praktika.data.backup.model.BackupSlotId

sealed class SlotSummary {
    data object Missing : SlotSummary()

    data class Valid(val sequence: Long) : SlotSummary()

    data object Invalid : SlotSummary()

    data object Unreadable : SlotSummary()

    data class Ambiguous(val matchCount: Int) : SlotSummary()

    data object TooLarge : SlotSummary()

    fun displayText(slot: BackupSlotId): String = when (this) {
        Missing -> "${slot.label()}: Missing"
        is Valid -> "${slot.label()}: Valid(seq=$sequence)"
        Invalid -> "${slot.label()}: Invalid"
        Unreadable -> "${slot.label()}: Unreadable"
        is Ambiguous -> "${slot.label()}: Ambiguous(count=$matchCount)"
        TooLarge -> "${slot.label()}: TooLarge"
    }
}

sealed class SafStorageProofUiState {
    data object Disconnected : SafStorageProofUiState()

    data object Connecting : SafStorageProofUiState()

    data class Connected(
        val providerAuthority: String?,
        val readGrant: Boolean,
        val writeGrant: Boolean,
        val restoredFromHint: Boolean = false,
    ) : SafStorageProofUiState()

    data object Inspecting : SafStorageProofUiState()

    data class ReadyEmptyFolder(
        val aSummary: SlotSummary,
        val bSummary: SlotSummary,
    ) : SafStorageProofUiState()

    data class FolderNotEmpty(
        val aSummary: SlotSummary,
        val bSummary: SlotSummary,
    ) : SafStorageProofUiState()

    data object Running : SafStorageProofUiState()

    data class ProofPassed(
        val aSummary: SlotSummary,
        val bSummary: SlotSummary,
        val latest: BackupSlotId,
        val documentUriRediscoveryWorked: Boolean,
    ) : SafStorageProofUiState()

    data class Failure(val safeReason: String) : SafStorageProofUiState()
}

sealed class SafStorageProofUiEvent {
    data object RequestPicker : SafStorageProofUiEvent()
}

enum class SafStorageProofFailure(val code: String) {
    FolderNotEmpty("TEST_FOLDER_NOT_EMPTY"),
    AmbiguousSlot("UNSAFE_TEST_FOLDER_AMBIGUOUS"),
    A1WriteFailed("A1_WRITE_FAILED"),
    A1InspectMismatch("A1_INSPECT_MISMATCH"),
    B2WriteFailed("B2_WRITE_FAILED"),
    B2InspectMismatch("B2_INSPECT_MISMATCH"),
    A3WriteFailed("A3_WRITE_FAILED"),
    A3InspectMismatch("A3_INSPECT_MISMATCH"),
    SelectorMismatch("SELECTOR_MISMATCH"),
    EnvelopeAssemblyFailed("ENVELOPE_ASSEMBLY_FAILED"),
    PermissionLost("PERMISSION_LOST"),
    Unavailable("UNAVAILABLE"),
    ProviderError("PROVIDER_ERROR"),
    Cancelled("CANCELLED"),
}

sealed class SafStorageProofRunResult {
    data class Passed(
        val aSequence: Long,
        val bSequence: Long,
        val latest: BackupSlotId,
        val documentUriRediscoveryWorked: Boolean,
    ) : SafStorageProofRunResult()

    data class Failed(val failure: SafStorageProofFailure, val detail: String? = null) :
        SafStorageProofRunResult()
}

private fun BackupSlotId.label(): String = when (this) {
    BackupSlotId.A -> "A"
    BackupSlotId.B -> "B"
}
