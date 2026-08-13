package com.me4hik.praktika.ui.saf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.ui.restore.OpenDocumentTreeWithFlagsContract

@Composable
fun SafStorageProofEffects(viewModel: SafStorageProofViewModel) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithFlagsContract(),
    ) { result ->
        viewModel.onPickerResult(result)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SafStorageProofUiEvent.RequestPicker -> pickerLauncher.launch(null)
            }
        }
    }
}

@Composable
fun SafStorageProofHarness(
    viewModel: SafStorageProofViewModel,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    SafStorageProofEffects(viewModel)
    SafStorageProofScreen(
        uiState = uiState,
        onConnectClicked = viewModel::onConnectClicked,
        onInspectClicked = viewModel::onInspectClicked,
        onRunProofClicked = viewModel::onRunProofClicked,
        onReloadConnectionClicked = viewModel::onReloadConnectionClicked,
    )
}
