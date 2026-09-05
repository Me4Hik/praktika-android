// PROMPT 153 — map MissedOccurrenceDetail list → detail UiState (preserve query order)
package com.me4hik.praktika.ui.analytics

import com.me4hik.praktika.data.read.analytics.MissedDetailFilter
import com.me4hik.praktika.data.read.analytics.MissedOccurrenceDateTimeFormatter
import com.me4hik.praktika.data.read.analytics.MissedOccurrenceDetail

object MissedOccurrencesDetailMapper {
    fun toUiState(
        filter: MissedDetailFilter,
        details: List<MissedOccurrenceDetail>,
        formatter: MissedOccurrenceDateTimeFormatter,
    ): MissedOccurrencesDetailUiState {
        if (details.isEmpty()) {
            return MissedOccurrencesDetailUiState.Empty
        }
        return when (filter) {
            is MissedDetailFilter.ByWeekday -> MissedOccurrencesDetailUiState.Content(
                mode = MissedOccurrencesDetailMode.Weekday(dayOfWeek = filter.dayOfWeek),
                rows = details.map { detail ->
                    MissedOccurrenceRowUi(
                        occurrenceId = detail.occurrenceId,
                        questionId = detail.questionId,
                        questionText = detail.questionTextSnapshot,
                        formattedDateTime = formatter.formatWeekdayDetailDateTime(detail),
                    )
                },
            )
            is MissedDetailFilter.ByQuestion -> MissedOccurrencesDetailUiState.Content(
                mode = MissedOccurrencesDetailMode.Question(
                    questionId = filter.questionId,
                    questionText = details.first().questionTextSnapshot,
                ),
                rows = details.map { detail ->
                    MissedOccurrenceRowUi(
                        occurrenceId = detail.occurrenceId,
                        questionId = detail.questionId,
                        questionText = null,
                        formattedDateTime = formatter.formatQuestionDetailDateTime(detail),
                    )
                },
            )
        }
    }
}
