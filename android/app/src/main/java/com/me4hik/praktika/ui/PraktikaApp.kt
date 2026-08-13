// 04.08.2026 Reminder App cursor by Me4Hik START - корневой Compose-контейнер
// 05.08.2026 Main Screen cursor by Me4Hik START - PracticeRootViewModel wiring
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - saved-state factory owner
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission и settings callbacks
// 10.08.2026 Post-release fixes cursor by Me4Hik START - split app vs channel settings intents
package com.me4hik.praktika.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.notification.NotificationPermissionRepository
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
fun PraktikaApp(
    runtime: PraktikaRuntime,
    activity: ComponentActivity,
    permissionLauncher: (String) -> Unit,
) {
    val factory = remember(runtime, activity) {
        (runtime.notificationPermissionRepository as? NotificationPermissionRepository)
            ?.bindRationaleChecker {
                activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            }
        PracticeRootViewModelFactory(
            owner = activity,
            runtime = runtime,
            onRequestPostNotifications = {
                permissionLauncher(Manifest.permission.POST_NOTIFICATIONS)
            },
            onOpenAppNotificationSettings = {
                activity.startActivity(
                    runtime.notificationPermissionRepository.createAppNotificationSettingsIntent(),
                )
            },
            onOpenChannelSettings = {
                val soundEnabled = runBlocking {
                    runtime.soundPreferenceRepository.soundEnabled.first()
                }
                activity.startActivity(
                    runtime.notificationPermissionRepository.createChannelSettingsIntent(soundEnabled),
                )
            },
            onOpenExactAlarmSettings = {
                runtime.exactAlarmCapabilityRepository.recordSettingsCta("home_card_intent")
                activity.startActivity(
                    runtime.exactAlarmCapabilityRepository.createRequestExactAlarmIntent(),
                )
            },
        )
    }
    val viewModel: PracticeRootViewModel = viewModel(factory = factory)
    AppNavigation(viewModel = viewModel, runtime = runtime)
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
