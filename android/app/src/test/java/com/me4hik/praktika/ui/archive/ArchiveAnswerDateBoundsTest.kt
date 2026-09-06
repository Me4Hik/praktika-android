// 06.09.2026 Archive period bounds cursor by Me4Hik START - unit tests for answer date bounds
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveAnswerDateBoundsTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun emptyEntries_returnsNull() {
        assertNull(ArchiveAnswerDateBounds.fromEntries(emptyList(), zone))
    }

    @Test
    fun singleDay_minEqualsMax() {
        val day = LocalDate.of(2026, 8, 7)
        val bounds = ArchiveAnswerDateBounds.fromEntries(
            listOf(entry(day, 10, 0), entry(day, 22, 0)),
            zone,
        )!!
        assertEquals(day.toEpochDay(), bounds.earliestEpochDay)
        assertEquals(day.toEpochDay(), bounds.latestEpochDay)
        assertTrue(bounds.contains(day.toEpochDay()))
        assertFalse(bounds.contains(day.minusDays(1).toEpochDay()))
        assertFalse(bounds.contains(day.plusDays(1).toEpochDay()))
    }

    @Test
    fun multiDay_continuousRangeIncludesHoleDays() {
        val first = LocalDate.of(2026, 8, 5)
        val last = LocalDate.of(2026, 8, 10)
        val hole = LocalDate.of(2026, 8, 7)
        val bounds = ArchiveAnswerDateBounds.fromEntries(
            listOf(entry(first, 12, 0), entry(last, 9, 0)),
            zone,
        )!!
        assertEquals(first.toEpochDay(), bounds.earliestEpochDay)
        assertEquals(last.toEpochDay(), bounds.latestEpochDay)
        assertTrue(bounds.contains(hole.toEpochDay()))
        assertTrue(bounds.contains(first.toEpochDay()))
        assertTrue(bounds.contains(last.toEpochDay()))
        assertFalse(bounds.contains(first.minusDays(1).toEpochDay()))
        assertFalse(bounds.contains(last.plusDays(1).toEpochDay()))
    }

    @Test
    fun midnightBoundary_matchesArchiveDateGroupingZoneLocalDate() {
        val dayOne = LocalDate.of(2026, 8, 7)
        val dayTwo = LocalDate.of(2026, 8, 8)
        val entries = listOf(entry(dayOne, 23, 59), entry(dayTwo, 0, 1))
        val grouped = ArchiveDateGrouping.groupDates(entries, zone)
        val bounds = ArchiveAnswerDateBounds.fromEntries(entries, zone)!!
        assertEquals(grouped.minOf { it.date }.toEpochDay(), bounds.earliestEpochDay)
        assertEquals(grouped.maxOf { it.date }.toEpochDay(), bounds.latestEpochDay)
        assertEquals(dayOne.toEpochDay(), bounds.earliestEpochDay)
        assertEquals(dayTwo.toEpochDay(), bounds.latestEpochDay)
    }

    @Test
    fun utcInstantNearMoscowMidnight_usesZoneLocalDay() {
        // 2026-08-07 00:30 MSK == 2026-08-06 21:30 UTC → archive day is Aug 7 in Moscow
        val moscowMorning = ZonedDateTime.of(2026, 8, 7, 0, 30, 0, 0, zone).toInstant()
        val entry = sampleEntry(1, moscowMorning.toEpochMilli())
        val bounds = ArchiveAnswerDateBounds.fromEntries(listOf(entry), zone)!!
        assertEquals(LocalDate.of(2026, 8, 7).toEpochDay(), bounds.earliestEpochDay)
        val utcDay = moscowMorning.atZone(ZoneId.of("UTC")).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 6), utcDay)
        assertFalse(bounds.contains(utcDay.toEpochDay()))
    }

    @Test
    fun sanitizeInitial_clearsWhenOutsideOrMissingBounds() {
        val bounds = ArchiveAnswerDateBounds(10L, 20L)
        assertEquals(15L, ArchiveAnswerDateBounds.sanitizeInitialEpochDay(15L, bounds))
        assertNull(ArchiveAnswerDateBounds.sanitizeInitialEpochDay(9L, bounds))
        assertNull(ArchiveAnswerDateBounds.sanitizeInitialEpochDay(21L, bounds))
        assertNull(ArchiveAnswerDateBounds.sanitizeInitialEpochDay(15L, null))
        assertNull(ArchiveAnswerDateBounds.sanitizeInitialEpochDay(null, bounds))
    }

    @Test
    fun materialUtcCell_matchesCivilEpochDay() {
        val day = LocalDate.of(2026, 8, 7)
        val utcMillis = ArchivePeriodUtcDates.epochDayToUtcMidnightMillis(day.toEpochDay())
        assertEquals(day.toEpochDay(), ArchivePeriodUtcDates.millisToEpochDay(utcMillis))
    }

    private fun entry(day: LocalDate, hour: Int, minute: Int): ArchiveEntry {
        val epoch = ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        return sampleEntry(answerId = day.toEpochDay() * 100 + hour, epoch = epoch)
    }

    private fun sampleEntry(answerId: Long, epoch: Long): ArchiveEntry {
        return ArchiveEntry(
            answerId = answerId,
            occurrenceId = answerId,
            questionId = 1,
            questionText = "Q",
            answerText = "A",
            answeredAtEpochMillis = epoch,
            plannedAtEpochMillis = epoch,
            cycleNumber = 1,
            cyclePosition = 1,
        )
    }
}
// 06.09.2026 Archive period bounds cursor by Me4Hik END
