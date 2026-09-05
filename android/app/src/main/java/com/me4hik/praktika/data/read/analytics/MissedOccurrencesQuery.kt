// PROMPT 149 — pure MISSED_BY_TIME filter + newest→oldest sort
package com.me4hik.praktika.data.read.analytics

import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

object MissedOccurrencesQuery {
    fun filter(
        terminals: List<AnalyticsTerminalOccurrenceRow>,
        filter: MissedDetailFilter,
    ): List<MissedOccurrenceDetail> {
        return terminals
            .asSequence()
            .filter { it.status == QuestionOccurrenceStatus.MISSED_BY_TIME }
            .map { row -> toDetail(row) }
            .filter { detail -> matches(detail, filter) }
            .sortedWith(
                compareByDescending<MissedOccurrenceDetail> { it.completedAtEpochMillis }
                    .thenByDescending { it.occurrenceId },
            )
            .toList()
    }

    private fun matches(detail: MissedOccurrenceDetail, filter: MissedDetailFilter): Boolean {
        return when (filter) {
            is MissedDetailFilter.ByWeekday -> detail.dayOfWeek == filter.dayOfWeek
            is MissedDetailFilter.ByQuestion -> detail.questionId == filter.questionId
        }
    }

    private fun toDetail(row: AnalyticsTerminalOccurrenceRow): MissedOccurrenceDetail {
        val zoned = AnalyticsEventTime.atZone(row.completedAtEpochMillis, row.zoneId)
        return MissedOccurrenceDetail(
            occurrenceId = row.occurrenceId,
            questionId = row.questionId,
            questionTextSnapshot = row.questionTextSnapshot,
            completedAtEpochMillis = row.completedAtEpochMillis,
            zoneId = row.zoneId,
            dayOfWeek = zoned.dayOfWeek,
            zonedDateTime = zoned,
        )
    }
}
