// PROMPT 129 — pure Kotlin analytics aggregator (event timezone, no device TZ)
package com.me4hik.praktika.data.read.analytics

import com.me4hik.praktika.data.local.model.AnalyticsDeferEventRow
import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

object AnalyticsAggregator {
    private const val WEEKDAY_TERMINAL_MIN = 5
    private const val WEEKDAY_EVENT_MIN = 3
    private const val QUESTION_TERMINAL_MIN = 5
    private const val DURATION_EVENT_MIN = 5

    fun aggregate(
        terminals: List<AnalyticsTerminalOccurrenceRow>,
        defers: List<AnalyticsDeferEventRow>,
    ): AnalyticsSnapshot {
        val validTerminals = terminals.filter {
            it.status == QuestionOccurrenceStatus.ANSWERED ||
                it.status == QuestionOccurrenceStatus.SKIPPED_BY_USER ||
                it.status == QuestionOccurrenceStatus.MISSED_BY_TIME
        }

        val deferCountsByOccurrence = defers.groupingBy { it.occurrenceId }.eachCount()
        val occurrencesWithAnyDefer = deferCountsByOccurrence.keys

        return AnalyticsSnapshot(
            weekdays = buildWeekdayMetrics(validTerminals, defers, occurrencesWithAnyDefer),
            questions = buildQuestionMetrics(validTerminals, defers, occurrencesWithAnyDefer),
            deferDuration = buildDeferDurationMetrics(defers),
            multiDefer = buildMultiDeferMetrics(deferCountsByOccurrence),
        )
    }

    fun rankQuestionsByMissedRateDesc(
        questions: List<AnalyticsQuestionMetrics>,
    ): List<AnalyticsQuestionMetrics> {
        return questions.sortedWith(rateThenNumeratorThenTerminalThenId(rate = { it.missedRate }, numerator = { it.missedCount }))
    }

    fun rankQuestionsByDeferredOccurrenceRateDesc(
        questions: List<AnalyticsQuestionMetrics>,
    ): List<AnalyticsQuestionMetrics> {
        return questions.sortedWith(
            rateThenNumeratorThenTerminalThenId(
                rate = { it.deferredOccurrenceRate },
                numerator = { it.deferredOccurrenceCount },
            ),
        )
    }

    private fun rateThenNumeratorThenTerminalThenId(
        rate: (AnalyticsQuestionMetrics) -> Double,
        numerator: (AnalyticsQuestionMetrics) -> Int,
    ): Comparator<AnalyticsQuestionMetrics> {
        return compareByDescending<AnalyticsQuestionMetrics>(rate)
            .thenByDescending(numerator)
            .thenByDescending { it.terminalCount }
            .thenBy { it.questionId }
    }

    private fun buildWeekdayMetrics(
        terminals: List<AnalyticsTerminalOccurrenceRow>,
        defers: List<AnalyticsDeferEventRow>,
        occurrencesWithAnyDefer: Set<Long>,
    ): List<AnalyticsWeekdayMetrics> {
        val terminalsByDay = terminals.groupBy { weekdayOf(it.completedAtEpochMillis, it.zoneId) }
        val deferEventsByDay = defers.groupBy { weekdayOf(it.occurredAtEpochMillis, it.zoneId) }

        return DayOfWeek.entries.map { day ->
            val dayTerminals = terminalsByDay[day].orEmpty()
            val terminalCount = dayTerminals.size
            val answeredCount = dayTerminals.count { it.status == QuestionOccurrenceStatus.ANSWERED }
            val rejectedCount = dayTerminals.count { it.status == QuestionOccurrenceStatus.SKIPPED_BY_USER }
            val missedCount = dayTerminals.count { it.status == QuestionOccurrenceStatus.MISSED_BY_TIME }
            val deferredOccurrenceCount = dayTerminals.count { it.occurrenceId in occurrencesWithAnyDefer }
            val deferEventCount = deferEventsByDay[day].orEmpty().size

            AnalyticsWeekdayMetrics(
                dayOfWeek = day,
                terminalCount = terminalCount,
                answeredCount = answeredCount,
                rejectedCount = rejectedCount,
                missedCount = missedCount,
                answeredRate = rate(answeredCount, terminalCount),
                rejectedRate = rate(rejectedCount, terminalCount),
                missedRate = rate(missedCount, terminalCount),
                deferredOccurrenceCount = deferredOccurrenceCount,
                deferredOccurrenceRate = rate(deferredOccurrenceCount, terminalCount),
                deferEventCount = deferEventCount,
                answeredInsightEligible = insightEligible(terminalCount, answeredCount),
                rejectedInsightEligible = insightEligible(terminalCount, rejectedCount),
                missedInsightEligible = insightEligible(terminalCount, missedCount),
                deferredInsightEligible = insightEligible(terminalCount, deferredOccurrenceCount),
            )
        }
    }

    private fun buildQuestionMetrics(
        terminals: List<AnalyticsTerminalOccurrenceRow>,
        defers: List<AnalyticsDeferEventRow>,
        occurrencesWithAnyDefer: Set<Long>,
    ): List<AnalyticsQuestionMetrics> {
        val terminalsByQuestion = terminals.groupBy { it.questionId }
        val defersByQuestion = defers.groupBy { it.questionId }
        val questionIds = (terminalsByQuestion.keys + defersByQuestion.keys).sorted()

        return questionIds.map { questionId ->
            val questionTerminals = terminalsByQuestion[questionId].orEmpty()
            val questionDefers = defersByQuestion[questionId].orEmpty()
            val terminalCount = questionTerminals.size
            val answeredCount = questionTerminals.count { it.status == QuestionOccurrenceStatus.ANSWERED }
            val rejectedCount = questionTerminals.count { it.status == QuestionOccurrenceStatus.SKIPPED_BY_USER }
            val missedCount = questionTerminals.count { it.status == QuestionOccurrenceStatus.MISSED_BY_TIME }
            val deferredOccurrenceCount = questionTerminals.count { it.occurrenceId in occurrencesWithAnyDefer }
            val identity = resolveQuestionIdentity(questionTerminals)

            AnalyticsQuestionMetrics(
                questionId = questionId,
                questionTextSnapshot = identity.text,
                cyclePosition = identity.cyclePosition,
                terminalCount = terminalCount,
                answeredCount = answeredCount,
                rejectedCount = rejectedCount,
                missedCount = missedCount,
                deferredOccurrenceCount = deferredOccurrenceCount,
                deferEventCount = questionDefers.size,
                answeredRate = rate(answeredCount, terminalCount),
                rejectedRate = rate(rejectedCount, terminalCount),
                missedRate = rate(missedCount, terminalCount),
                deferredOccurrenceRate = rate(deferredOccurrenceCount, terminalCount),
                questionInsightEligible = terminalCount >= QUESTION_TERMINAL_MIN,
            )
        }
    }

    private fun resolveQuestionIdentity(
        terminals: List<AnalyticsTerminalOccurrenceRow>,
    ): QuestionIdentity {
        if (terminals.isEmpty()) {
            return QuestionIdentity(text = "", cyclePosition = 0)
        }
        val latest = terminals.maxWith(
            compareBy<AnalyticsTerminalOccurrenceRow> { it.completedAtEpochMillis }
                .thenBy { it.occurrenceId },
        )
        return QuestionIdentity(
            text = latest.questionTextSnapshot,
            cyclePosition = latest.cyclePosition,
        )
    }

    private fun buildDeferDurationMetrics(
        defers: List<AnalyticsDeferEventRow>,
    ): AnalyticsDeferDurationMetrics {
        val histogram = linkedMapOf(
            5 to 0,
            10 to 0,
            15 to 0,
            30 to 0,
        )
        var totalMinutes = 0L
        for (event in defers) {
            val minutes = event.durationMinutes
            if (minutes in DeferDurationOptions.ALLOWED_MINUTES) {
                histogram[minutes] = (histogram[minutes] ?: 0) + 1
            }
            totalMinutes += minutes.toLong()
        }
        val total = defers.size
        return AnalyticsDeferDurationMetrics(
            countByDurationMinutes = histogram.toMap(),
            totalDeferEvents = total,
            averageDurationMinutes = if (total == 0) null else totalMinutes.toDouble() / total.toDouble(),
            modeDurationMinutes = modeDurationMinutes(histogram),
            durationInsightEligible = total >= DURATION_EVENT_MIN,
        )
    }

    /**
     * Mode among known defer durations. On equal frequency, the smallest durationMinutes wins.
     */
    internal fun modeDurationMinutes(countByDurationMinutes: Map<Int, Int>): Int? {
        val positive = countByDurationMinutes.filterValues { it > 0 }
        if (positive.isEmpty()) {
            return null
        }
        val maxCount = positive.values.maxOrNull() ?: return null
        return positive
            .filterValues { it == maxCount }
            .keys
            .minOrNull()
    }

    private fun buildMultiDeferMetrics(
        deferCountsByOccurrence: Map<Long, Int>,
    ): AnalyticsMultiDeferMetrics {
        if (deferCountsByOccurrence.isEmpty()) {
            return AnalyticsMultiDeferMetrics(
                occurrencesWithMultipleDefers = 0,
                maxDefersOnSingleOccurrence = 0,
            )
        }
        return AnalyticsMultiDeferMetrics(
            occurrencesWithMultipleDefers = deferCountsByOccurrence.values.count { it >= 2 },
            maxDefersOnSingleOccurrence = deferCountsByOccurrence.values.maxOrNull() ?: 0,
        )
    }

    private fun insightEligible(terminalCount: Int, eventCount: Int): Boolean {
        return terminalCount >= WEEKDAY_TERMINAL_MIN && eventCount >= WEEKDAY_EVENT_MIN
    }

    private fun rate(numerator: Int, denominator: Int): Double {
        if (denominator <= 0) {
            return 0.0
        }
        return numerator.toDouble() / denominator.toDouble()
    }

    private fun weekdayOf(epochMillis: Long, zoneId: String): DayOfWeek {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.of("UTC") }
        return Instant.ofEpochMilli(epochMillis).atZone(zone).dayOfWeek
    }

    private data class QuestionIdentity(
        val text: String,
        val cyclePosition: Int,
    )
}
