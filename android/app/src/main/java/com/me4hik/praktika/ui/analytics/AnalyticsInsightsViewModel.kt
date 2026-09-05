// PROMPT 133 — Analytics Insights ViewModel (suspend snapshot on open)
package com.me4hik.praktika.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.read.AnalyticsReadRepository
import com.me4hik.praktika.data.read.analytics.AnalyticsAggregator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnalyticsInsightsViewModel(
    private val analyticsReadRepository: AnalyticsReadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyticsInsightsUiState>(AnalyticsInsightsUiState.Loading)
    val uiState: StateFlow<AnalyticsInsightsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = AnalyticsInsightsUiState.Loading
            try {
                val terminals = analyticsReadRepository.getTerminalRows()
                val defers = analyticsReadRepository.getDeferRows()
                _uiState.value = if (terminals.isEmpty() && defers.isEmpty()) {
                    AnalyticsInsightsUiState.Empty
                } else {
                    AnalyticsInsightsMapper.toUiState(
                        AnalyticsAggregator.aggregate(terminals, defers),
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.value = AnalyticsInsightsUiState.Error(
                    message = throwable.message ?: "Analytics read failed",
                )
            }
        }
    }
}
