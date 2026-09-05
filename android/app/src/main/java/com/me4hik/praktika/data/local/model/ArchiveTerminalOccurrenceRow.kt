// PROMPT 115 — terminal occurrence + optional answer projection for archive history
package com.me4hik.praktika.data.local.model

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

data class ArchiveTerminalOccurrenceRow(
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val status: QuestionOccurrenceStatus,
    val completedAtEpochMillis: Long?,
    val answerId: Long?,
    val answerText: String?,
    val answerCreatedAtEpochMillis: Long?,
)
