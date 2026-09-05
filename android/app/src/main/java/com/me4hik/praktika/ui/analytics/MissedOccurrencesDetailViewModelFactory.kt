// PROMPT 153 — ViewModel factory for missed occurrences detail (typed filter)
package com.me4hik.praktika.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.me4hik.praktika.data.read.analytics.MissedDetailFilter
import com.me4hik.praktika.runtime.PraktikaRuntime

class MissedOccurrencesDetailViewModelFactory(
    private val runtime: PraktikaRuntime,
    private val filter: MissedDetailFilter,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MissedOccurrencesDetailViewModel::class.java)) {
            return MissedOccurrencesDetailViewModel(
                analyticsReadRepository = runtime.analyticsReadRepository,
                filter = filter,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
