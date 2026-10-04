package com.me4hik.praktika.ui.practice

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.me4hik.praktika.runtime.PraktikaRuntime
import kotlinx.coroutines.flow.first

/**
 * Collects [PracticeRootViewModel.uiEvents] with the *current* Activity / permission launcher.
 * Safe across locale/config recreate because handlers are not retained inside the ViewModel.
 */
@Composable
fun PracticeRootUiEventEffects(
    viewModel: PracticeRootViewModel,
    runtime: PraktikaRuntime,
    activity: ComponentActivity,
    permissionLauncher: (String) -> Unit,
) {
    val currentActivity by rememberUpdatedState(activity)
    val currentPermissionLauncher by rememberUpdatedState(permissionLauncher)

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                PracticeRootUiEvent.RequestPostNotifications -> {
                    currentPermissionLauncher(Manifest.permission.POST_NOTIFICATIONS)
                }
                PracticeRootUiEvent.OpenAppNotificationSettings -> {
                    currentActivity.startActivity(
                        runtime.notificationPermissionRepository.createAppNotificationSettingsIntent(),
                    )
                }
                PracticeRootUiEvent.OpenChannelSettings -> {
                    val soundEnabled = runtime.soundPreferenceRepository.soundEnabled.first()
                    currentActivity.startActivity(
                        runtime.notificationPermissionRepository.createChannelSettingsIntent(soundEnabled),
                    )
                }
                PracticeRootUiEvent.OpenExactAlarmSettings -> {
                    runtime.exactAlarmCapabilityRepository.recordSettingsCta("home_card_intent")
                    currentActivity.startActivity(
                        runtime.exactAlarmCapabilityRepository.createRequestExactAlarmIntent(),
                    )
                }
            }
        }
    }
}
