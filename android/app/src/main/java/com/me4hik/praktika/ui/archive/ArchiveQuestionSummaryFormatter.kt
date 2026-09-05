// PROMPT 123 — compact mixed-count supporting line for Archive questions list
package com.me4hik.praktika.ui.archive

enum class ArchiveSummaryCountKind {
    Answer,
    Rejected,
    Missed,
    Deferred,
}

data class ArchiveSummaryCountSegment(
    val kind: ArchiveSummaryCountKind,
    val count: Int,
)

object ArchiveQuestionSummaryFormatter {
    const val SEPARATOR = " · "

    /**
     * Non-zero counts in fixed product order: Answers → Rejected → Missed → Deferred.
     * Empty list when every count is zero.
     */
    fun nonzeroSegments(
        answerCount: Int,
        rejectedCount: Int,
        missedCount: Int,
        deferredCount: Int,
    ): List<ArchiveSummaryCountSegment> {
        val segments = ArrayList<ArchiveSummaryCountSegment>(4)
        if (answerCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Answer, answerCount)
        }
        if (rejectedCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Rejected, rejectedCount)
        }
        if (missedCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Missed, missedCount)
        }
        if (deferredCount > 0) {
            segments += ArchiveSummaryCountSegment(ArchiveSummaryCountKind.Deferred, deferredCount)
        }
        return segments
    }

    fun join(segmentTexts: List<String>): String {
        return segmentTexts.joinToString(SEPARATOR)
    }
}
