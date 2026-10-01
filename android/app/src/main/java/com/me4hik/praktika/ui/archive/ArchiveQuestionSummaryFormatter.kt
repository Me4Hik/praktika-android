// PROMPT 123 — compact mixed-count supporting line for Archive questions list
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik START - terminal outcomes only
package com.me4hik.praktika.ui.archive

enum class ArchiveSummaryCountKind {
    Answer,
    Rejected,
    Missed,
}

data class ArchiveSummaryCountSegment(
    val kind: ArchiveSummaryCountKind,
    val count: Int,
)

object ArchiveQuestionSummaryFormatter {
    const val SEPARATOR = " · "

    /**
     * Non-zero counts in fixed product order: Answers → Rejected → Missed.
     * Empty list when every count is zero.
     */
    fun nonzeroSegments(
        answerCount: Int,
        rejectedCount: Int,
        missedCount: Int,
    ): List<ArchiveSummaryCountSegment> {
        val segments = ArrayList<ArchiveSummaryCountSegment>(3)
        if (answerCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Answer, answerCount)
        }
        if (rejectedCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Rejected, rejectedCount)
        }
        if (missedCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Missed, missedCount)
        }
        return segments
    }

    fun join(segmentTexts: List<String>): String {
        return segmentTexts.joinToString(SEPARATOR)
    }
}
// 01.10.2026 Archive T3 summary terminal counts cursor by Me4Hik END
