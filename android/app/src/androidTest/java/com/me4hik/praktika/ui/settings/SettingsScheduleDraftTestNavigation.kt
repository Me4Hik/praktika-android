// 06.08.2026 Settings Time Affordance cursor by Me4Hik START - Settings route with controlled draft changes
package com.me4hik.praktika.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.Routes
import com.me4hik.praktika.diagnostics.FakeDiagnosticReportSubmitter
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.ui.HomeScreen
import com.me4hik.praktika.ui.SettingsScreen
import com.me4hik.praktika.ui.practice.CurrentOccurrenceUiModel
import com.me4hik.praktika.ui.practice.MainContentUiState

@Composable
fun SettingsScheduleDraftTestNavigation(
    runtime: PraktikaRuntime,
    startOnSettings: Boolean,
    applyDraftChanges: Map<Int, Int> = emptyMap(),
) {
    val navController = rememberNavController()
    LaunchedEffect(startOnSettings) {
        if (startOnSettings) {
            navController.navigate(Routes.SETTINGS)
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                content = MainContentUiState.Scheduled(
                    occurrence = CurrentOccurrenceUiModel(
                        occurrenceId = 1L,
                        questionId = 1,
                        cyclePosition = 1,
                        questionText = "Question 1",
                        status = QuestionOccurrenceStatus.SCHEDULED,
                        plannedAtText = "11:00",
                        availableUntilText = "15:00",
                    ),
                ),
                notificationCard = null,
                onNotificationCardAction = {},
                onOpenQuestion = {},
                onOpenArchive = {},
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) { backStackEntry ->
            val settingsViewModel: SettingsViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = SettingsViewModelFactory(
                    backStackEntry,
                    runtime,
                    FakeDiagnosticReportSubmitter(),
                ),
            )
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            var showDirtyDialog by remember { mutableStateOf(false) }
            LaunchedEffect(settingsViewModel, applyDraftChanges) {
                applyDraftChanges.forEach { (slotIndex, minutes) ->
                    settingsViewModel.onSlotTimeChanged(slotIndex, minutes)
                }
            }
            LaunchedEffect(settingsViewModel) {
                settingsViewModel.navigation.collect { event ->
                    when (event) {
                        SettingsNavigationEvent.ConfirmDiscardChanges -> {
                            showDirtyDialog = true
                        }
                        SettingsNavigationEvent.NavigateBackClean -> {
                            showDirtyDialog = false
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                        is SettingsNavigationEvent.OpenNotificationSettings -> Unit
                    }
                }
            }
            SettingsScreen(
                uiState = settingsUiState,
                onSlotTimeChange = settingsViewModel::onSlotTimeChanged,
                onSoundEnabledChanged = settingsViewModel::onSoundEnabledChanged,
                onDeferDurationMinutesChanged = settingsViewModel::onDeferDurationMinutesChanged,
                onOpenNotificationSettings = settingsViewModel::onNotificationSettingsClicked,
                onTogglePauseState = settingsViewModel::togglePauseState,
                onBack = settingsViewModel::onBackRequested,
                onStayOnDirtyBack = { showDirtyDialog = false },
                onDiscardChanges = {
                    showDirtyDialog = false
                    settingsViewModel.confirmDiscardChanges()
                },
                showDirtyDialog = showDirtyDialog,
            )
        }
    }
}
// 06.08.2026 Settings Time Affordance cursor by Me4Hik END
