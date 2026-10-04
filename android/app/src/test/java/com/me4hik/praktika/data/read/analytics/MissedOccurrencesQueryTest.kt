// PROMPT 149 — MissedOccurrencesQuery + formatter + AnalyticsEventTime unit tests
package com.me4hik.praktika.data.read.analytics

import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.AppLanguage
import com.me4hik.praktika.data.preferences.AppLocaleController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MissedOccurrencesQueryTest {

    private val formatter = MissedOccurrenceDateTimeFormatter()

    @Before
    fun setUp() {
        AppLocaleController.apply(AppLanguage.RU)
    }

    @Test
    fun filter_includesOnlyMissedByTime() {
        val friday = localMillis(2026, 9, 4, 12, 0, ZONE)
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, friday, "Missed"),
            terminal(2, 1, QuestionOccurrenceStatus.ANSWERED, friday, "Answered"),
            terminal(3, 1, QuestionOccurrenceStatus.SKIPPED_BY_USER, friday, "Skipped"),
        )

        val result = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByQuestion(1),
        )

        assertEquals(listOf(1L), result.map { it.occurrenceId })
        assertEquals("Missed", result.single().questionTextSnapshot)
    }

    @Test
    fun byWeekday_returnsOnlyMatchingStoredZoneWeekday() {
        // 2026-09-04 23:30 Europe/Kyiv = Friday; same instant is Saturday in UTC.
        val fridayKyiv = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        val saturdayKyiv = localMillis(2026, 9, 5, 10, 0, "Europe/Kyiv")
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, fridayKyiv, zoneId = "Europe/Kyiv"),
            terminal(2, 2, QuestionOccurrenceStatus.MISSED_BY_TIME, saturdayKyiv, zoneId = "Europe/Kyiv"),
        )

        val fridayOnly = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByWeekday(DayOfWeek.FRIDAY),
        )
        val saturdayOnly = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByWeekday(DayOfWeek.SATURDAY),
        )

        assertEquals(listOf(1L), fridayOnly.map { it.occurrenceId })
        assertEquals(DayOfWeek.FRIDAY, fridayOnly.single().dayOfWeek)
        assertEquals(listOf(2L), saturdayOnly.map { it.occurrenceId })
    }

    @Test
    fun byWeekday_usesStoredZoneNotUtcWallClock() {
        val fridayKyiv = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, fridayKyiv, zoneId = "Europe/Kyiv"),
        )

        val friday = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByWeekday(DayOfWeek.FRIDAY),
        )
        val saturday = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByWeekday(DayOfWeek.SATURDAY),
        )

        assertEquals(1, friday.size)
        assertTrue(saturday.isEmpty())
    }

    @Test
    fun deviceTimezoneChange_doesNotAffectWeekdayFilter() {
        val completedAt = localMillis(2026, 9, 4, 23, 30, "Europe/Kyiv")
        val terminals = listOf(
            terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, completedAt, zoneId = "Europe/Kyiv"),
        )
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val underUtc = MissedOccurrencesQuery.filter(
                terminals,
                MissedDetailFilter.ByWeekday(DayOfWeek.FRIDAY),
            )
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val underLa = MissedOccurrencesQuery.filter(
                terminals,
                MissedDetailFilter.ByWeekday(DayOfWeek.FRIDAY),
            )

            assertEquals(1, underUtc.size)
            assertEquals(1, underLa.size)
            assertEquals(underUtc.single().dayOfWeek, underLa.single().dayOfWeek)
            assertEquals(DayOfWeek.FRIDAY, underUtc.single().dayOfWeek)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun invalidZoneId_fallsBackToUtcDeterministically() {
        // Saturday 01:00 Europe/Kyiv = Friday 22:00 UTC — weekday diverges under UTC fallback.
        val saturdayKyiv = localMillis(2026, 9, 5, 1, 0, "Europe/Kyiv")
        val terminals = listOf(
            terminal(
                occurrenceId = 1,
                questionId = 1,
                status = QuestionOccurrenceStatus.MISSED_BY_TIME,
                completedAt = saturdayKyiv,
                zoneId = "Not/ARealZone",
            ),
        )

        val asUtc = AnalyticsEventTime.atZone(saturdayKyiv, "Not/ARealZone")
        assertEquals("UTC", asUtc.zone.id)
        assertEquals(DayOfWeek.FRIDAY, asUtc.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, AnalyticsEventTime.dayOfWeek(saturdayKyiv, "Europe/Kyiv"))

        val friday = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByWeekday(DayOfWeek.FRIDAY),
        )
        val saturday = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByWeekday(DayOfWeek.SATURDAY),
        )
        assertEquals(1, friday.size)
        assertTrue(saturday.isEmpty())
    }

    @Test
    fun byQuestion_exactQuestionIdOnly() {
        val t = localMillis(2026, 9, 4, 12, 0, ZONE)
        val terminals = listOf(
            terminal(1, 10, QuestionOccurrenceStatus.MISSED_BY_TIME, t, "Q10"),
            terminal(2, 20, QuestionOccurrenceStatus.MISSED_BY_TIME, t, "Q20"),
            terminal(3, 10, QuestionOccurrenceStatus.MISSED_BY_TIME, t + 1_000, "Q10b"),
        )

        val result = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByQuestion(10),
        )

        assertEquals(listOf(3L, 1L), result.map { it.occurrenceId })
        assertTrue(result.all { it.questionId == 10 })
    }

    @Test
    fun sort_newestCompletedAtFirst_thenOccurrenceIdDesc() {
        val older = localMillis(2026, 9, 1, 10, 0, ZONE)
        val newer = localMillis(2026, 9, 8, 14, 20, ZONE)
        val terminals = listOf(
            terminal(10, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, older),
            terminal(20, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, newer),
            terminal(30, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, newer),
        )

        val result = MissedOccurrencesQuery.filter(
            terminals,
            MissedDetailFilter.ByQuestion(1),
        )

        assertEquals(listOf(30L, 20L, 10L), result.map { it.occurrenceId })
    }

    @Test
    fun weekdayFormatter_usesRuPatternAndStoredZone() {
        val completedAt = localMillis(2026, 9, 8, 14, 20, ZONE)
        val detail = MissedOccurrencesQuery.filter(
            listOf(terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, completedAt)),
            MissedDetailFilter.ByQuestion(1),
        ).single()

        assertEquals("8 сентября 2026 · 14:20", formatter.formatWeekdayDetailDateTime(detail))
    }

    @Test
    fun questionFormatter_includesWeekdayRuAndStoredZone() {
        val completedAt = localMillis(2026, 9, 8, 14, 20, ZONE)
        val detail = MissedOccurrencesQuery.filter(
            listOf(terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, completedAt)),
            MissedDetailFilter.ByQuestion(1),
        ).single()

        assertEquals(DayOfWeek.TUESDAY, detail.dayOfWeek)
        assertEquals(
            "вторник, 8 сентября 2026 · 14:20",
            formatter.formatQuestionDetailDateTime(detail),
        )
    }

    @Test
    fun formatter_ignoresDeviceTimezoneDefault() {
        val completedAt = localMillis(2026, 9, 8, 14, 20, ZONE)
        val detail = MissedOccurrencesQuery.filter(
            listOf(terminal(1, 1, QuestionOccurrenceStatus.MISSED_BY_TIME, completedAt)),
            MissedDetailFilter.ByQuestion(1),
        ).single()
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("8 сентября 2026 · 14:20", formatter.formatWeekdayDetailDateTime(detail))
            assertEquals(
                "вторник, 8 сентября 2026 · 14:20",
                formatter.formatQuestionDetailDateTime(detail),
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun terminal(
        occurrenceId: Long,
        questionId: Int,
        status: QuestionOccurrenceStatus,
        completedAt: Long,
        text: String = "Q$questionId",
        zoneId: String = ZONE,
    ) = AnalyticsTerminalOccurrenceRow(
        occurrenceId = occurrenceId,
        questionId = questionId,
        questionTextSnapshot = text,
        cyclePosition = questionId,
        status = status,
        completedAtEpochMillis = completedAt,
        zoneId = zoneId,
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
