// PROMPT 133 — ViewModel factory for Analytics Insights (no screen yet)
package com.me4hik.praktika.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.me4hik.praktika.runtime.PraktikaRuntime

class AnalyticsInsightsViewModelFactory(
    private val runtime: PraktikaRuntime,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsInsightsViewModel::class.java)) {
            return AnalyticsInsightsViewModel(
                analyticsReadRepository = runtime.analyticsReadRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
