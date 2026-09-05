// PROMPT 119 — question-level summary from mixed ArchiveHistoryEvent feed
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveHistoryEvent

data class ArchiveQuestionHistorySummary(
    val questionId: Int,
    val questionText: String,
    val cyclePosition: Int,
    val latestEventAtEpochMillis: Long,
    val answerCount: Int,
    val rejectedCount: Int,
    val missedCount: Int,
    val deferredCount: Int,
)

object ArchiveHistoryQuestionGrouping {
    fun summarize(events: List<ArchiveHistoryEvent>): List<ArchiveQuestionHistorySummary> {
        if (events.isEmpty()) {
            return emptyList()
        }
        return events
            .groupBy { it.questionId }
            .map { (questionId, grouped) ->
                val latest = grouped.maxWith(
                    compareBy<ArchiveHistoryEvent> { it.eventAtEpochMillis }
                        .thenBy { it.stableKey },
                )
                var answerCount = 0
                var rejectedCount = 0
                var missedCount = 0
                var deferredCount = 0
                for (event in grouped) {
                    when (event) {
                        is ArchiveHistoryEvent.Answer -> answerCount++
                        is ArchiveHistoryEvent.Rejected -> rejectedCount++
                        is ArchiveHistoryEvent.Missed -> missedCount++
                        is ArchiveHistoryEvent.Deferred -> deferredCount++
                    }
                }
                ArchiveQuestionHistorySummary(
                    questionId = questionId,
                    questionText = latest.questionTextSnapshot,
                    cyclePosition = latest.cyclePosition,
                    latestEventAtEpochMillis = latest.eventAtEpochMillis,
                    answerCount = answerCount,
                    rejectedCount = rejectedCount,
                    missedCount = missedCount,
                    deferredCount = deferredCount,
                )
            }
            .sortedWith(
                compareByDescending<ArchiveQuestionHistorySummary> { it.latestEventAtEpochMillis }
                    .thenBy { it.questionId },
            )
    }
}
