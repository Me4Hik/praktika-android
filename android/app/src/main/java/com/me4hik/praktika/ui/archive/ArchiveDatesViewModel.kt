// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - ViewModel списка дат
package com.me4hik.praktika.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.read.ArchiveReadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ArchiveDatesViewModel(
    private val archiveReadRepository: ArchiveReadRepository,
    private val zoneIdProvider: ArchiveZoneIdProvider,
    private val displayFormatter: ArchiveDisplayFormatter,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveDatesUiState>(ArchiveDatesUiState.Loading)
    val uiState: StateFlow<ArchiveDatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            archiveReadRepository.observeEntries()
                .catch { throwable ->
                    _uiState.value = ArchiveDatesUiState.Error(
                        message = throwable.message ?: "Archive read failed",
                    )
                }
                .collect { entries ->
                    val zoneId = zoneIdProvider.currentZoneId()
                    val grouped = ArchiveDateGrouping.groupDates(entries, zoneId)
                    _uiState.value = if (grouped.isEmpty()) {
                        ArchiveDatesUiState.Empty
                    } else {
                        ArchiveDatesUiState.Content(
                            dates = grouped.map { item ->
                                ArchiveDateListItem(
                                    epochDay = item.date.toEpochDay(),
                                    dateText = displayFormatter.formatDate(
                                        item.date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                                        zoneId,
                                    ),
                                    answerCount = item.answerCount,
                                )
                            },
                        )
                    }
                }
        }
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
