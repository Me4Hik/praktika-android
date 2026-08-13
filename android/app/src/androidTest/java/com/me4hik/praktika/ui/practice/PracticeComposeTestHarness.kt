// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - await ViewModel read layer before Compose assertions
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - saved-state factory owner
package com.me4hik.praktika.ui.practice

import androidx.savedstate.SavedStateRegistryOwner
import com.me4hik.praktika.runtime.PraktikaRuntime
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

object PracticeComposeTestHarness {
    fun createViewModel(
        owner: SavedStateRegistryOwner,
        runtime: PraktikaRuntime,
        onRequestPostNotifications: () -> Unit = {},
        onOpenAppNotificationSettings: () -> Unit = {},
        onOpenChannelSettings: () -> Unit = {},
    ): PracticeRootViewModel {
        return PracticeRootViewModelFactory(
            owner = owner,
            runtime = runtime,
            onRequestPostNotifications = onRequestPostNotifications,
            onOpenAppNotificationSettings = onOpenAppNotificationSettings,
            onOpenChannelSettings = onOpenChannelSettings,
        ).create(PracticeRootViewModel::class.java)
    }

    suspend fun awaitInitialUiState(viewModel: PracticeRootViewModel) {
        withTimeout(10_000) {
            viewModel.uiState.filter { it !is PracticeUiState.Loading }.first()
        }
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
