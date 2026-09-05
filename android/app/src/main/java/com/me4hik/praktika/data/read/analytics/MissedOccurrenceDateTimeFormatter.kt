// PROMPT 149 — RU datetime lines for missed drill-down (stored event zone)
package com.me4hik.praktika.data.read.analytics

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats [MissedOccurrenceDetail.zonedDateTime] for UI.
 * Always uses the detail's already-resolved event timezone (never device default).
 */
class MissedOccurrenceDateTimeFormatter(
    private val locale: Locale = Locale.forLanguageTag("ru"),
) {
    private val weekdayDetailFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm", locale)
    private val questionDetailFormatter =
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · HH:mm", locale)

    fun formatWeekdayDetailDateTime(detail: MissedOccurrenceDetail): String {
        return detail.zonedDateTime.format(weekdayDetailFormatter)
    }

    fun formatQuestionDetailDateTime(detail: MissedOccurrenceDetail): String {
        return detail.zonedDateTime.format(questionDetailFormatter)
    }
}
