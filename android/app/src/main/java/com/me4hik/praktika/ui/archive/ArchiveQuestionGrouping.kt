// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - группировка архива по questionId
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry

data class ArchiveQuestionGroup(
    val questionId: Int,
    val questionText: String,
    val answerCount: Int,
    val latestAnsweredAtEpochMillis: Long,
)

object ArchiveQuestionGrouping {
    fun groupQuestions(entries: List<ArchiveEntry>): List<ArchiveQuestionGroup> {
        if (entries.isEmpty()) {
            return emptyList()
        }
        return entries
            .groupBy { it.questionId }
            .map { (questionId, groupedEntries) ->
                val newestEntry = groupedEntries.maxWith(
                    compareBy<ArchiveEntry> { it.answeredAtEpochMillis }
                        .thenBy { it.answerId },
                )
                ArchiveQuestionGroup(
                    questionId = questionId,
                    questionText = newestEntry.questionText,
                    answerCount = groupedEntries.size,
                    latestAnsweredAtEpochMillis = newestEntry.answeredAtEpochMillis,
                )
            }
            .sortedWith(
                compareByDescending<ArchiveQuestionGroup> { it.latestAnsweredAtEpochMillis }
                    .thenBy { it.questionId },
            )
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
