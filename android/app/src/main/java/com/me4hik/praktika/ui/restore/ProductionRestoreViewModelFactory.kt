// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 production restore ViewModel factory
package com.me4hik.praktika.ui.restore

import android.content.Context
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.me4hik.praktika.runtime.PraktikaRuntime

class ProductionRestoreViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val applicationContext: Context,
    private val runtime: PraktikaRuntime,
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        if (modelClass.isAssignableFrom(ProductionRestoreViewModel::class.java)) {
            val dependencies = ProductionRestoreDependencies.create(
                context = applicationContext,
                runtime = runtime,
            )
            return ProductionRestoreViewModel(
                gateway = dependencies.gateway,
                previewMapper = dependencies.previewMapper,
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A session controller
                restoreSessionController = dependencies.restoreSessionController,
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
