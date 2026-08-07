// 04.08.2026 DB Refactoring cursor by Me4Hik START - проекция архивного JOIN-запроса
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik START - plannedAt для ArchiveEntry
package com.me4hik.praktika.data.local.model

data class AnswerArchiveRow(
    val answerId: Long,
    val occurrenceId: Long,
    val questionId: Int,
    val questionText: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val plannedAtEpochMillis: Long,
    val answerText: String,
    val createdAtEpochMillis: Long,
)
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik END
// 04.08.2026 DB Refactoring cursor by Me4Hik END
