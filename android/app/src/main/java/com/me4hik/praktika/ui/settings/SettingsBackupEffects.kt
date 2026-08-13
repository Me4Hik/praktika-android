// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup effects
package com.me4hik.praktika.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.me4hik.praktika.ui.restore.OpenDocumentTreeWithFlagsContract

@Composable
fun SettingsBackupEffects(
    viewModel: SettingsViewModel,
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeWithFlagsContract(),
    ) { result ->
        viewModel.onBackupFolderPickerResult(
            uri = result?.uri,
            grantFlags = result?.flags ?: 0,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.backupEffects.collect { effect ->
            when (effect) {
                is SettingsBackupEffect.LaunchFolderPicker -> {
                    pickerLauncher.launch(null)
                }
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
