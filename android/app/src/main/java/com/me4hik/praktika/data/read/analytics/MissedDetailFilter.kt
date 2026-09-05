// PROMPT 149 — missed drill-down filter (weekday or question)
package com.me4hik.praktika.data.read.analytics

import java.time.DayOfWeek

sealed interface MissedDetailFilter {
    data class ByWeekday(val dayOfWeek: DayOfWeek) : MissedDetailFilter

    data class ByQuestion(val questionId: Int) : MissedDetailFilter
}
