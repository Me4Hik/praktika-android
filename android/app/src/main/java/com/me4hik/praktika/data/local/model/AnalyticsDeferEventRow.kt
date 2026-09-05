// PROMPT 129 — analytics defer_events projection (no schema change)
package com.me4hik.praktika.data.local.model

data class AnalyticsDeferEventRow(
    val deferEventId: Long,
    val occurrenceId: Long,
    val questionId: Int,
    val occurredAtEpochMillis: Long,
    val durationMinutes: Int,
    val zoneId: String,
)
