// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.2 restore overlay policy
package com.me4hik.praktika.ui.restore

internal object ProductionRestoreOverlayPolicy {
    fun showsCloseIcon(uiState: ProductionRestoreUiState): Boolean {
        return when (uiState) {
            ProductionRestoreUiState.Hidden,
            is ProductionRestoreUiState.Restoring,
            is ProductionRestoreUiState.RestoreSuccess,
            is ProductionRestoreUiState.RuntimeSyncWarning,
            -> false
            else -> true
        }
    }

    fun isBackDismissible(uiState: ProductionRestoreUiState): Boolean {
        return when (uiState) {
            ProductionRestoreUiState.Hidden,
            is ProductionRestoreUiState.Restoring,
            is ProductionRestoreUiState.RestoreSuccess,
            is ProductionRestoreUiState.RuntimeSyncWarning,
            -> false
            else -> true
        }
    }

    fun showsBusyIndicator(uiState: ProductionRestoreUiState): Boolean {
        return when (uiState) {
            ProductionRestoreUiState.CheckingEligibility,
            ProductionRestoreUiState.ChoosingFolder,
            ProductionRestoreUiState.Connecting,
            ProductionRestoreUiState.Inspecting,
            is ProductionRestoreUiState.Restoring,
            -> true
            else -> false
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
