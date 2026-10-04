// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - shared Compose harness helpers
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - shared ViewModel factory helper
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
    ): PracticeRootViewModel {
        return PracticeRootViewModelFactory(
            owner = owner,
            runtime = runtime,
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
