// PROMPT 133 — map AnalyticsSnapshot → UI models (deterministic, no device TZ)
package com.me4hik.praktika.ui.analytics

import com.me4hik.praktika.data.read.analytics.AnalyticsAggregator
import com.me4hik.praktika.data.read.analytics.AnalyticsSnapshot

object AnalyticsInsightsMapper {
    const val TOP_QUESTION_LIMIT: Int = 3

    fun toUiState(snapshot: AnalyticsSnapshot): AnalyticsInsightsUiState.Content {
        val weekdays = snapshot.weekdays
            .filter { it.terminalCount > 0 || it.deferEventCount > 0 }
            .map { day ->
                AnalyticsWeekdayInsightRow(
                    dayOfWeek = day.dayOfWeek,
                    terminalCount = day.terminalCount,
                    missed = AnalyticsOutcomeRate(
                        numerator = day.missedCount,
                        denominator = day.terminalCount,
                        rate = day.missedRate,
                        eligible = day.missedInsightEligible,
                    ),
                    deferred = AnalyticsOutcomeRate(
                        numerator = day.deferredOccurrenceCount,
                        denominator = day.terminalCount,
                        rate = day.deferredOccurrenceRate,
                        eligible = day.deferredInsightEligible,
                    ),
                    rejected = AnalyticsOutcomeRate(
                        numerator = day.rejectedCount,
                        denominator = day.terminalCount,
                        rate = day.rejectedRate,
                        eligible = day.rejectedInsightEligible,
                    ),
                    deferEventCount = day.deferEventCount,
                )
            }

        val topMissed = AnalyticsAggregator.rankQuestionsByMissedRateDesc(snapshot.questions)
            .take(TOP_QUESTION_LIMIT)
            .map { question ->
                AnalyticsQuestionInsightRow(
                    questionId = question.questionId,
                    questionText = question.questionTextSnapshot,
                    numerator = question.missedCount,
                    denominator = question.terminalCount,
                    rate = question.missedRate,
                    eligible = question.questionInsightEligible,
                )
            }

        val topDeferred = AnalyticsAggregator.rankQuestionsByDeferredOccurrenceRateDesc(snapshot.questions)
            .take(TOP_QUESTION_LIMIT)
            .map { question ->
                AnalyticsQuestionInsightRow(
                    questionId = question.questionId,
                    questionText = question.questionTextSnapshot,
                    numerator = question.deferredOccurrenceCount,
                    denominator = question.terminalCount,
                    rate = question.deferredOccurrenceRate,
                    eligible = question.questionInsightEligible,
                )
            }

        val duration = AnalyticsDurationInsight(
            totalDeferEvents = snapshot.deferDuration.totalDeferEvents,
            modeDurationMinutes = snapshot.deferDuration.modeDurationMinutes,
            averageDurationMinutes = snapshot.deferDuration.averageDurationMinutes,
            histogram = snapshot.deferDuration.countByDurationMinutes,
            eligible = snapshot.deferDuration.durationInsightEligible,
        )

        val hasAnyInsightEligible =
            snapshot.weekdays.any {
                it.missedInsightEligible ||
                    it.deferredInsightEligible ||
                    it.rejectedInsightEligible ||
                    it.answeredInsightEligible
            } ||
                snapshot.questions.any { it.questionInsightEligible } ||
                snapshot.deferDuration.durationInsightEligible

        return AnalyticsInsightsUiState.Content(
            weekdays = weekdays,
            topMissed = topMissed,
            topDeferred = topDeferred,
            duration = duration,
            hasAnyInsightEligible = hasAnyInsightEligible,
            multiDefer = snapshot.multiDefer,
        )
    }
}
