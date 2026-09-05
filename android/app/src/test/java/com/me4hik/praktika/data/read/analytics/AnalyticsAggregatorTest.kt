// PROMPT 129 — pure aggregator unit tests (event timezone, rates, rankings)
package com.me4hik.praktika.data.read.analytics

import com.me4hik.praktika.data.local.model.AnalyticsDeferEventRow
import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class AnalyticsAggregatorTest {

    @Test
    fun weekdayBucketing_usesStoredZoneIdNotUtcWallClock() {
        // 2026-09-04 23:30 Europe/Kyiv = Friday; same instant is Saturday in UTC.
        val fridayKyiv = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        val terminals = listOf(
            terminal(
                occurrenceId = 1,
                questionId = 1,
                status = QuestionOccurrenceStatus.MISSED_BY_TIME,
                completedAt = fridayKyiv,
                zoneId = "Europe/Kyiv",
            ),
        )

        val snapshot = AnalyticsAggregator.aggregate(terminals, emptyList())
        val friday = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        val saturday = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.SATURDAY }

        assertEquals(1, friday.terminalCount)
        assertEquals(1, friday.missedCount)
        assertEquals(0, saturday.terminalCount)
    }

    @Test
    fun deviceTimezoneChange_doesNotAffectWeekdayBucket() {
        val completedAt = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        val terminals = listOf(
            terminal(
                occurrenceId = 1,
                questionId = 1,
                status = QuestionOccurrenceStatus.ANSWERED,
                completedAt = completedAt,
                zoneId = "Europe/Kyiv",
            ),
        )
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = AnalyticsAggregator.aggregate(terminals, emptyList())
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val underLa = AnalyticsAggregator.aggregate(terminals, emptyList())

            assertEquals(
                underUtc.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }.terminalCount,
                underLa.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }.terminalCount,
            )
            assertEquals(1, underUtc.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }.terminalCount)
            assertEquals(0, underUtc.weekdays.single { it.dayOfWeek == DayOfWeek.SATURDAY }.terminalCount)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun singleDefer_countsOneDeferredOccurrenceAndOneEvent() {
        val friday = localMillis(2026, 9, 4, 12, 0, ZONE)
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, friday),
            terminal(2, 1, QuestionOccurrenceStatus.ANSWERED, friday),
        )
        val defers = listOf(
            defer(10, occurrenceId = 1, questionId = 1, occurredAt = friday, duration = 15),
        )

        val fridayMetrics = AnalyticsAggregator.aggregate(terminals, defers)
            .weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }

        assertEquals(2, fridayMetrics.terminalCount)
        assertEquals(1, fridayMetrics.deferredOccurrenceCount)
        assertEquals(1, fridayMetrics.deferEventCount)
        assertEquals(0.5, fridayMetrics.deferredOccurrenceRate, 1e-9)
    }

    @Test
    fun twoDefersSameOccurrence_occurrenceCountOne_eventCountTwo() {
        val friday = localMillis(2026, 9, 4, 12, 0, ZONE)
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, friday),
        )
        val defers = listOf(
            defer(10, occurrenceId = 1, questionId = 1, occurredAt = friday, duration = 5),
            defer(11, occurrenceId = 1, questionId = 1, occurredAt = friday + 60_000L, duration = 10),
        )

        val snapshot = AnalyticsAggregator.aggregate(terminals, defers)
        val fridayMetrics = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        val question = snapshot.questions.single()

        assertEquals(1, fridayMetrics.deferredOccurrenceCount)
        assertEquals(2, fridayMetrics.deferEventCount)
        assertEquals(1.0, fridayMetrics.deferredOccurrenceRate, 1e-9)
        assertEquals(1, question.deferredOccurrenceCount)
        assertEquals(2, question.deferEventCount)
        assertEquals(1.0, question.deferredOccurrenceRate, 1e-9)
        assertEquals(1, snapshot.multiDefer.occurrencesWithMultipleDefers)
        assertEquals(2, snapshot.multiDefer.maxDefersOnSingleOccurrence)
    }

    @Test
    fun outcomeRates_areCorrectForWeekdayAndQuestion() {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(2, 1, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(3, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day),
            terminal(4, 1, QuestionOccurrenceStatus.SKIPPED_BY_USER, day),
        )

        val snapshot = AnalyticsAggregator.aggregate(terminals, emptyList())
        val friday = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        val question = snapshot.questions.single()

        assertEquals(4, friday.terminalCount)
        assertEquals(2, friday.answeredCount)
        assertEquals(1, friday.missedCount)
        assertEquals(1, friday.rejectedCount)
        assertEquals(0.5, friday.answeredRate, 1e-9)
        assertEquals(0.25, friday.missedRate, 1e-9)
        assertEquals(0.25, friday.rejectedRate, 1e-9)

        assertEquals(4, question.terminalCount)
        assertEquals(0.5, question.answeredRate, 1e-9)
        assertEquals(0.25, question.missedRate, 1e-9)
        assertEquals(0.25, question.rejectedRate, 1e-9)
    }

    @Test
    fun perQuestionDenominator_isTerminalOccurrencesOnly() {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        val terminals = listOf(
            terminal(1, 7, QuestionOccurrenceStatus.MISSED_BY_TIME, day, text = "Q7", cyclePosition = 7),
            terminal(2, 7, QuestionOccurrenceStatus.ANSWERED, day, text = "Q7", cyclePosition = 7),
        )
        // Extra defer events on the same terminals must not inflate denominator.
        val defers = listOf(
            defer(1, 1, 7, day, 15),
            defer(2, 1, 7, day + 1, 15),
            defer(3, 2, 7, day + 2, 10),
        )

        val question = AnalyticsAggregator.aggregate(terminals, defers).questions.single()
        assertEquals(2, question.terminalCount)
        assertEquals(1, question.missedCount)
        assertEquals(0.5, question.missedRate, 1e-9)
        assertEquals(2, question.deferredOccurrenceCount)
        assertEquals(3, question.deferEventCount)
        assertEquals(1.0, question.deferredOccurrenceRate, 1e-9)
        assertEquals("Q7", question.questionTextSnapshot)
        assertEquals(7, question.cyclePosition)
    }

    @Test
    fun deferredOccurrenceRate_neverExceedsOne_evenWithManyDeferEvents() {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, day),
            terminal(2, 1, QuestionOccurrenceStatus.ANSWERED, day),
        )
        val defers = (1..10).map { index ->
            defer(
                deferEventId = index.toLong(),
                occurrenceId = 1L,
                questionId = 1,
                occurredAt = day + index,
                duration = 5,
            )
        }

        val snapshot = AnalyticsAggregator.aggregate(terminals, defers)
        val friday = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        val question = snapshot.questions.single()

        assertEquals(10, friday.deferEventCount)
        assertEquals(1, friday.deferredOccurrenceCount)
        assertTrue(friday.deferredOccurrenceRate <= 1.0)
        assertEquals(0.5, friday.deferredOccurrenceRate, 1e-9)
        assertTrue(question.deferredOccurrenceRate <= 1.0)
        assertEquals(0.5, question.deferredOccurrenceRate, 1e-9)
    }

    @Test
    fun deferDuration_histogramAvgAndMode() {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        val defers = listOf(
            defer(1, 1, 1, day, 5),
            defer(2, 1, 1, day, 15),
            defer(3, 2, 1, day, 15),
            defer(4, 2, 1, day, 30),
            defer(5, 3, 1, day, 15),
        )

        val duration = AnalyticsAggregator.aggregate(emptyList(), defers).deferDuration
        assertEquals(5, duration.totalDeferEvents)
        assertEquals(1, duration.countByDurationMinutes.getValue(5))
        assertEquals(0, duration.countByDurationMinutes.getValue(10))
        assertEquals(3, duration.countByDurationMinutes.getValue(15))
        assertEquals(1, duration.countByDurationMinutes.getValue(30))
        assertEquals(15, duration.modeDurationMinutes)
        assertEquals(16.0, duration.averageDurationMinutes!!, 1e-9)
        assertTrue(duration.durationInsightEligible)
    }

    @Test
    fun modeDuration_tieBreaksToSmallerMinutes() {
        val histogram = mapOf(5 to 2, 10 to 2, 15 to 1, 30 to 0)
        assertEquals(5, AnalyticsAggregator.modeDurationMinutes(histogram))
    }

    @Test
    fun modeDuration_emptyIsNull() {
        assertNull(AnalyticsAggregator.modeDurationMinutes(mapOf(5 to 0, 10 to 0, 15 to 0, 30 to 0)))
    }

    @Test
    fun questionRanking_tiesBrokenByNumeratorThenTerminalThenId() {
        val questions = listOf(
            questionMetrics(questionId = 3, missedRate = 0.5, missedCount = 2, terminalCount = 4),
            questionMetrics(questionId = 1, missedRate = 0.5, missedCount = 3, terminalCount = 6),
            questionMetrics(questionId = 2, missedRate = 0.5, missedCount = 3, terminalCount = 6),
            questionMetrics(questionId = 4, missedRate = 0.8, missedCount = 4, terminalCount = 5),
        )

        val ranked = AnalyticsAggregator.rankQuestionsByMissedRateDesc(questions)
        assertEquals(listOf(4, 1, 2, 3), ranked.map { it.questionId })
    }

    @Test
    fun deferredRanking_usesDeferredOccurrenceNumeratorOnTie() {
        val questions = listOf(
            questionMetrics(
                questionId = 2,
                deferredRate = 0.5,
                deferredOccurrenceCount = 2,
                terminalCount = 4,
            ),
            questionMetrics(
                questionId = 1,
                deferredRate = 0.5,
                deferredOccurrenceCount = 3,
                terminalCount = 6,
            ),
        )

        val ranked = AnalyticsAggregator.rankQuestionsByDeferredOccurrenceRateDesc(questions)
        assertEquals(listOf(1, 2), ranked.map { it.questionId })
    }

    @Test
    fun smallSampleEligibility_flags() {
        val day = localMillis(2026, 9, 4, 10, 0, ZONE)
        val smallTerminals = (1..4).map { id ->
            terminal(id.toLong(), 1, QuestionOccurrenceStatus.MISSED_BY_TIME, day)
        }
        val smallSnapshot = AnalyticsAggregator.aggregate(smallTerminals, emptyList())
        val smallFriday = smallSnapshot.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        assertFalse(smallFriday.missedInsightEligible)
        assertFalse(smallSnapshot.questions.single().questionInsightEligible)

        val eligibleTerminals = (1..5).map { id ->
            terminal(
                id.toLong(),
                1,
                if (id <= 3) QuestionOccurrenceStatus.MISSED_BY_TIME else QuestionOccurrenceStatus.ANSWERED,
                day,
            )
        }
        val eligible = AnalyticsAggregator.aggregate(eligibleTerminals, emptyList())
        val friday = eligible.weekdays.single { it.dayOfWeek == DayOfWeek.FRIDAY }
        assertTrue(friday.missedInsightEligible)
        assertFalse(friday.answeredInsightEligible) // answeredCount=2 < 3
        assertTrue(eligible.questions.single().questionInsightEligible)

        val durationNotReady = AnalyticsAggregator.aggregate(
            emptyList(),
            (1..4).map { defer(it.toLong(), 1, 1, day, 15) },
        ).deferDuration
        assertFalse(durationNotReady.durationInsightEligible)

        val durationReady = AnalyticsAggregator.aggregate(
            emptyList(),
            (1..5).map { defer(it.toLong(), 1, 1, day, 15) },
        ).deferDuration
        assertTrue(durationReady.durationInsightEligible)
    }

    @Test
    fun deferWeekday_usesDeferEventZoneId() {
        // Saturday 01:00 Tokyo; Friday evening in Kyiv — event zone must win.
        val occurredAt = localMillis(2026, 9, 5, 1, 0, "Asia/Tokyo")
        val completedAt = localMillis(2026, 9, 5, 2, 0, "Asia/Tokyo")
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.ANSWERED, completedAt, zoneId = "Asia/Tokyo"),
        )
        val defers = listOf(
            defer(1, 1, 1, occurredAt, 10, zoneId = "Asia/Tokyo"),
        )

        val snapshot = AnalyticsAggregator.aggregate(terminals, defers)
        val saturday = snapshot.weekdays.single { it.dayOfWeek == DayOfWeek.SATURDAY }
        assertEquals(1, saturday.terminalCount)
        assertEquals(1, saturday.deferEventCount)
        assertEquals(1, saturday.deferredOccurrenceCount)
    }

    private fun terminal(
        occurrenceId: Long,
        questionId: Int,
        status: QuestionOccurrenceStatus,
        completedAt: Long,
        zoneId: String = ZONE,
        text: String = "Q$questionId",
        cyclePosition: Int = questionId,
    ) = AnalyticsTerminalOccurrenceRow(
        occurrenceId = occurrenceId,
        questionId = questionId,
        questionTextSnapshot = text,
        cyclePosition = cyclePosition,
        status = status,
        completedAtEpochMillis = completedAt,
        zoneId = zoneId,
    )

    private fun defer(
        deferEventId: Long,
        occurrenceId: Long,
        questionId: Int,
        occurredAt: Long,
        duration: Int,
        zoneId: String = ZONE,
    ) = AnalyticsDeferEventRow(
        deferEventId = deferEventId,
        occurrenceId = occurrenceId,
        questionId = questionId,
        occurredAtEpochMillis = occurredAt,
        durationMinutes = duration,
        zoneId = zoneId,
    )

    private fun questionMetrics(
        questionId: Int,
        missedRate: Double = 0.0,
        missedCount: Int = 0,
        terminalCount: Int = 0,
        deferredRate: Double = 0.0,
        deferredOccurrenceCount: Int = 0,
    ) = AnalyticsQuestionMetrics(
        questionId = questionId,
        questionTextSnapshot = "Q$questionId",
        cyclePosition = questionId,
        terminalCount = terminalCount,
        answeredCount = 0,
        rejectedCount = 0,
        missedCount = missedCount,
        deferredOccurrenceCount = deferredOccurrenceCount,
        deferEventCount = 0,
        answeredRate = 0.0,
        rejectedRate = 0.0,
        missedRate = missedRate,
        deferredOccurrenceRate = deferredRate,
        questionInsightEligible = terminalCount >= 5,
    )

    private fun localMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zoneId: String,
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val ZONE = "Europe/Kyiv"
    }
}
