// PROMPT 133 — Analytics Insights UI models (no RU product copy)
package com.me4hik.praktika.ui.analytics

import com.me4hik.praktika.data.read.analytics.AnalyticsMultiDeferMetrics
import java.time.DayOfWeek

sealed interface AnalyticsInsightsUiState {
    data object Loading : AnalyticsInsightsUiState

    data object Empty : AnalyticsInsightsUiState

    data class Content(
        val weekdays: List<AnalyticsWeekdayInsightRow>,
        val topMissed: List<AnalyticsQuestionInsightRow>,
        val topDeferred: List<AnalyticsQuestionInsightRow>,
        val duration: AnalyticsDurationInsight,
        val hasAnyInsightEligible: Boolean,
        val multiDefer: AnalyticsMultiDeferMetrics,
    ) : AnalyticsInsightsUiState

    data class Error(
        val message: String,
    ) : AnalyticsInsightsUiState
}

data class AnalyticsOutcomeRate(
    val numerator: Int,
    val denominator: Int,
    val rate: Double,
    val eligible: Boolean,
)

data class AnalyticsWeekdayInsightRow(
    val dayOfWeek: DayOfWeek,
    val terminalCount: Int,
    val missed: AnalyticsOutcomeRate,
    val deferred: AnalyticsOutcomeRate,
    val rejected: AnalyticsOutcomeRate,
    val deferEventCount: Int,
)

data class AnalyticsQuestionInsightRow(
    val questionId: Int,
    val questionText: String,
    val numerator: Int,
    val denominator: Int,
    val rate: Double,
    val eligible: Boolean,
)

data class AnalyticsDurationInsight(
    val totalDeferEvents: Int,
    val modeDurationMinutes: Int?,
    val averageDurationMinutes: Double?,
    val histogram: Map<Int, Int>,
    val eligible: Boolean,
)
