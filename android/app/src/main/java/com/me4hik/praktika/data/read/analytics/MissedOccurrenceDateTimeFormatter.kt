// PROMPT 149 — datetime lines for missed drill-down (stored event zone)
package com.me4hik.praktika.data.read.analytics

import com.me4hik.praktika.data.preferences.AppLocaleController
import java.time.format.DateTimeFormatter

/**
 * Formats [MissedOccurrenceDetail.zonedDateTime] for UI.
 * Always uses the detail's already-resolved event timezone (never device default).
 */
class MissedOccurrenceDateTimeFormatter {
    fun formatWeekdayDetailDateTime(detail: MissedOccurrenceDetail): String {
        val formatter = DateTimeFormatter.ofPattern(
            "d MMMM yyyy · HH:mm",
            AppLocaleController.currentLocale(),
        )
        return detail.zonedDateTime.format(formatter)
    }

    fun formatQuestionDetailDateTime(detail: MissedOccurrenceDetail): String {
        val formatter = DateTimeFormatter.ofPattern(
            "EEEE, d MMMM yyyy · HH:mm",
            AppLocaleController.currentLocale(),
        )
        return detail.zonedDateTime.format(formatter)
    }
}
