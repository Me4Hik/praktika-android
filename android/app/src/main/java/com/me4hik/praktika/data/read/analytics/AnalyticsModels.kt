// PROMPT 129 — pure analytics aggregation models (no UI copy)
package com.me4hik.praktika.data.read.analytics

import java.time.DayOfWeek

data class AnalyticsWeekdayMetrics(
    val dayOfWeek: DayOfWeek,
    val terminalCount: Int,
    val answeredCount: Int,
    val rejectedCount: Int,
    val missedCount: Int,
    val answeredRate: Double,
    val rejectedRate: Double,
    val missedRate: Double,
    /** Distinct terminal occurrences on this weekday that have ≥1 defer event. */
    val deferredOccurrenceCount: Int,
    val deferredOccurrenceRate: Double,
    /** Raw defer_events whose occurredAt weekday (event zoneId) is this day. */
    val deferEventCount: Int,
    val answeredInsightEligible: Boolean,
    val rejectedInsightEligible: Boolean,
    val missedInsightEligible: Boolean,
    val deferredInsightEligible: Boolean,
)

data class AnalyticsQuestionMetrics(
    val questionId: Int,
    val questionTextSnapshot: String,
    val cyclePosition: Int,
    val terminalCount: Int,
    val answeredCount: Int,
    val rejectedCount: Int,
    val missedCount: Int,
    val deferredOccurrenceCount: Int,
    val deferEventCount: Int,
    val answeredRate: Double,
    val rejectedRate: Double,
    val missedRate: Double,
    val deferredOccurrenceRate: Double,
    val questionInsightEligible: Boolean,
)

data class AnalyticsDeferDurationMetrics(
    val countByDurationMinutes: Map<Int, Int>,
    val totalDeferEvents: Int,
    val averageDurationMinutes: Double?,
    /** Null when no defer events. On frequency tie: smallest durationMinutes wins. */
    val modeDurationMinutes: Int?,
    val durationInsightEligible: Boolean,
)

data class AnalyticsMultiDeferMetrics(
    val occurrencesWithMultipleDefers: Int,
    val maxDefersOnSingleOccurrence: Int,
)

data class AnalyticsSnapshot(
    val weekdays: List<AnalyticsWeekdayMetrics>,
    val questions: List<AnalyticsQuestionMetrics>,
    val deferDuration: AnalyticsDeferDurationMetrics,
    val multiDefer: AnalyticsMultiDeferMetrics,
)
