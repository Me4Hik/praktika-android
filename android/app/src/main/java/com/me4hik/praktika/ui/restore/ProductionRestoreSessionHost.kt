// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.2 production restore session host
package com.me4hik.praktika.ui.restore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProductionRestoreSessionHost(
    viewModel: ProductionRestoreViewModel,
) {
    val sessionActive by viewModel.sessionActive.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (sessionActive) {
        ProductionRestoreOverlay(
            uiState = uiState,
            onChooseFolder = viewModel::onChooseFolder,
            onRestoreConfirmed = viewModel::onRestoreConfirmed,
            onDismiss = viewModel::onDismiss,
            onSuccessAcknowledged = viewModel::onSuccessAcknowledged,
            onRuntimeWarningContinue = viewModel::onRuntimeWarningContinue,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
