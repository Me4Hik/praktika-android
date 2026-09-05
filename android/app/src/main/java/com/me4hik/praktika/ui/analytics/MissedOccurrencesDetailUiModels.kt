// PROMPT 153 — Missed occurrences detail UI models (no RU product titles)
package com.me4hik.praktika.ui.analytics

import java.time.DayOfWeek

sealed interface MissedOccurrencesDetailUiState {
    data object Loading : MissedOccurrencesDetailUiState

    data object Empty : MissedOccurrencesDetailUiState

    data class Content(
        val mode: MissedOccurrencesDetailMode,
        val rows: List<MissedOccurrenceRowUi>,
    ) : MissedOccurrencesDetailUiState

    data class Error(
        val message: String,
    ) : MissedOccurrencesDetailUiState
}

sealed interface MissedOccurrencesDetailMode {
    data class Weekday(
        val dayOfWeek: DayOfWeek,
    ) : MissedOccurrencesDetailMode

    data class Question(
        val questionId: Int,
        val questionText: String,
    ) : MissedOccurrencesDetailMode
}

data class MissedOccurrenceRowUi(
    val occurrenceId: Long,
    val questionId: Int,
    val questionText: String?,
    val formattedDateTime: String,
)
