// PROMPT 119 — question-level summary from mixed ArchiveHistoryEvent feed
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - summarize occurrence units
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik START - no raw defer in summary
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveOccurrenceOutcome
import com.me4hik.praktika.data.read.ArchiveOccurrenceUnit

data class ArchiveQuestionHistorySummary(
    val questionId: Int,
    val questionText: String,
    val cyclePosition: Int,
    val latestEventAtEpochMillis: Long,
    val answerCount: Int,
    val rejectedCount: Int,
    val missedCount: Int,
)

object ArchiveHistoryQuestionGrouping {
    fun summarize(units: List<ArchiveOccurrenceUnit>): List<ArchiveQuestionHistorySummary> {
        if (units.isEmpty()) {
            return emptyList()
        }
        return units
            .groupBy { it.questionId }
            .map { (questionId, grouped) ->
                val latest = grouped.maxWith(
                    compareBy<ArchiveOccurrenceUnit> { it.eventAtEpochMillis }
                        .thenBy { it.occurrenceId },
                )
                var answerCount = 0
                var rejectedCount = 0
                var missedCount = 0
                for (unit in grouped) {
                    when (unit.outcome) {
                        ArchiveOccurrenceOutcome.ANSWERED -> answerCount++
                        ArchiveOccurrenceOutcome.REJECTED -> rejectedCount++
                        ArchiveOccurrenceOutcome.MISSED -> missedCount++
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
                )
            }
            // 03.10.2026 Archive fixed numbering cursor by Me4Hik START - list order = cyclePosition ASC
            .sortedWith(
                compareBy<ArchiveQuestionHistorySummary> { it.cyclePosition }
                    .thenBy { it.questionId },
            )
            // 03.10.2026 Archive fixed numbering cursor by Me4Hik END
    }
}
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik END
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
