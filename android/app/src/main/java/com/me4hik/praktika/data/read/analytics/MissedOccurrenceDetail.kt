// PROMPT 149 — missed drill-down read model (no cycleNumber)
package com.me4hik.praktika.data.read.analytics

import java.time.DayOfWeek
import java.time.ZonedDateTime

data class MissedOccurrenceDetail(
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val completedAtEpochMillis: Long,
    val zoneId: String,
    val dayOfWeek: DayOfWeek,
    val zonedDateTime: ZonedDateTime,
)
