package com.me4hik.praktika.ui.restore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.ui.restore.OpenDocumentTreeWithFlagsContract

@Composable
fun RestoreProofEffects(viewModel: RestoreProofViewModel) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithFlagsContract(),
    ) { result ->
        viewModel.onPickerResult(result)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RestoreProofUiEvent.RequestPicker -> pickerLauncher.launch(null)
            }
        }
    }
}

@Composable
fun RestoreProofHarness(
    viewModel: RestoreProofViewModel,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    RestoreProofEffects(viewModel)
    RestoreProofScreen(
        uiState = uiState,
        onConnectClicked = viewModel::onConnectClicked,
        onInspectClicked = viewModel::onInspectClicked,
        onPrepareClicked = viewModel::onPrepareClicked,
        onPreviewClicked = viewModel::onPreviewClicked,
        onRestoreClicked = viewModel::onRestoreClicked,
        onRetryRestoreClicked = viewModel::onRetryRestoreClicked,
        onReloadConnectionClicked = viewModel::onReloadConnectionClicked,
    )
}
