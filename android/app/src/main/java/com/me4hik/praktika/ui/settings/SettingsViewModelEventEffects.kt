package com.me4hik.praktika.ui.settings

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Single active collector for [SettingsViewModel] snackbar/navigation while a Settings-subtree
 * destination is composed. Call from at most one destination at a time (Settings or Notifications).
 */
@Composable
fun SettingsViewModelEventEffects(
    viewModel: SettingsViewModel,
    handleBackNavigation: Boolean,
    onSnackbarMessage: (SettingsSnackbarEvent) -> Unit,
    onConfirmDiscardChanges: () -> Unit = {},
    onNavigateBackClean: () -> Unit = {},
    onOpenNotificationSettings: (Intent) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.snackbar.collect(onSnackbarMessage)
    }
    LaunchedEffect(viewModel, handleBackNavigation) {
        viewModel.navigation.collect { event ->
            when (event) {
                SettingsNavigationEvent.ConfirmDiscardChanges -> {
                    if (handleBackNavigation) {
                        onConfirmDiscardChanges()
                    }
                }
                SettingsNavigationEvent.NavigateBackClean -> {
                    if (handleBackNavigation) {
                        onNavigateBackClean()
                    }
                }
                is SettingsNavigationEvent.OpenNotificationSettings -> {
                    onOpenNotificationSettings(event.intent)
                }
            }
        }
    }
}
