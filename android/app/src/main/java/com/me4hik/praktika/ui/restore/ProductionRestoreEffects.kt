// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.1 production restore effects
package com.me4hik.praktika.ui.restore

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ProductionRestoreEffects(
    viewModel: ProductionRestoreViewModel,
    onRestoreCommitted: (practiceStarted: Boolean) -> Unit = {},
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithFlagsContract(),
    ) { result ->
        viewModel.onFolderPickerResult(
            uri = result?.uri,
            grantFlags = result?.flags ?: 0,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProductionRestoreEffect.LaunchFolderPicker -> {
                    if (viewModel.uiState.value != ProductionRestoreUiState.ChoosingFolder) {
                        return@collect
                    }
                    pickerLauncher.launch(parseInitialUriHint(effect.initialUriHint))
                }

                is ProductionRestoreEffect.RestoreCommitted -> {
                    onRestoreCommitted(effect.practiceStarted)
                }
            }
        }
    }
}

internal fun parseInitialUriHint(initialUriHint: String?): Uri? {
    if (initialUriHint.isNullOrBlank()) {
        return null
    }
    val parsed = runCatching { Uri.parse(initialUriHint.trim()) }.getOrNull() ?: return null
    if (parsed.scheme != ContentResolver.SCHEME_CONTENT) {
        return null
    }
    return parsed
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
