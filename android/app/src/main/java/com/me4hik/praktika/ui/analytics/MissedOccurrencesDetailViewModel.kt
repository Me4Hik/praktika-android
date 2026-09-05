// PROMPT 153 — Missed occurrences detail ViewModel (suspend snapshot + refresh)
package com.me4hik.praktika.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.read.AnalyticsReadRepository
import com.me4hik.praktika.data.read.analytics.MissedDetailFilter
import com.me4hik.praktika.data.read.analytics.MissedOccurrenceDateTimeFormatter
import com.me4hik.praktika.data.read.analytics.MissedOccurrencesQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MissedOccurrencesDetailViewModel(
    private val analyticsReadRepository: AnalyticsReadRepository,
    private val filter: MissedDetailFilter,
    private val dateTimeFormatter: MissedOccurrenceDateTimeFormatter = MissedOccurrenceDateTimeFormatter(),
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<MissedOccurrencesDetailUiState>(MissedOccurrencesDetailUiState.Loading)
    val uiState: StateFlow<MissedOccurrencesDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MissedOccurrencesDetailUiState.Loading
            try {
                val terminals = analyticsReadRepository.getTerminalRows()
                val details = MissedOccurrencesQuery.filter(terminals, filter)
                _uiState.value = MissedOccurrencesDetailMapper.toUiState(
                    filter = filter,
                    details = details,
                    formatter = dateTimeFormatter,
                )
            } catch (throwable: Throwable) {
                _uiState.value = MissedOccurrencesDetailUiState.Error(
                    message = throwable.message ?: "Missed occurrences read failed",
                )
            }
        }
    }
}
