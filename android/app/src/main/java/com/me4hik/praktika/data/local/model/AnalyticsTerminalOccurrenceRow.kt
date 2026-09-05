// PROMPT 129 — analytics terminal occurrence projection (no schema change)
package com.me4hik.praktika.data.local.model

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

data class AnalyticsTerminalOccurrenceRow(
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cyclePosition: Int,
    val status: QuestionOccurrenceStatus,
    val completedAtEpochMillis: Long,
    val zoneId: String,
)
