package com.me4hik.praktika.ui.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.me4hik.praktika.measurement.AnalyticsTracker
import com.me4hik.praktika.measurement.DebugAnalyticsProvider
import com.me4hik.praktika.measurement.ProductAnalyticsEvents
import com.me4hik.praktika.runtime.PraktikaRuntime

class AnalyticsDiagnosticsViewModel(
    private val tracker: AnalyticsTracker,
    private val debugProvider: DebugAnalyticsProvider,
) : ViewModel() {
    val entries = debugProvider.entries

    fun sendTestEvent() {
        tracker.track(ProductAnalyticsEvents.debugTestEvent())
    }

    fun clear() {
        debugProvider.clear()
    }
}

class AnalyticsDiagnosticsViewModelFactory(
    private val runtime: PraktikaRuntime,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AnalyticsDiagnosticsViewModel::class.java)) {
            return AnalyticsDiagnosticsViewModel(
                tracker = runtime.analyticsTracker,
                debugProvider = runtime.debugAnalyticsProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
