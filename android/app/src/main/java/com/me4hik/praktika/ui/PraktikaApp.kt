// 04.08.2026 Reminder App cursor by Me4Hik START - корневой Compose-контейнер
// 05.08.2026 Main Screen cursor by Me4Hik START - PracticeRootViewModel wiring
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - saved-state factory owner
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission и settings callbacks
// 10.08.2026 Post-release fixes cursor by Me4Hik START - split app vs channel settings intents
package com.me4hik.praktika.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.notification.NotificationPermissionRepository
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.ui.practice.PracticeRootUiEventEffects
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelFactory

@Composable
fun PraktikaApp(
    runtime: PraktikaRuntime,
    activity: ComponentActivity,
    permissionLauncher: (String) -> Unit,
) {
    DisposableEffect(runtime, activity) {
        (runtime.notificationPermissionRepository as? NotificationPermissionRepository)
            ?.bindRationaleChecker {
                activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            }
        onDispose { }
    }
    val factory = remember(runtime, activity) {
        PracticeRootViewModelFactory(
            owner = activity,
            runtime = runtime,
        )
    }
    val viewModel: PracticeRootViewModel = viewModel(factory = factory)
    PracticeRootUiEventEffects(
        viewModel = viewModel,
        runtime = runtime,
        activity = activity,
        permissionLauncher = permissionLauncher,
    )
    AppNavigation(viewModel = viewModel, runtime = runtime)
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
