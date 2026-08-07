// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - unit tests ArchiveDayRange
package com.me4hik.praktika.ui.archive

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveDayRangeTest {
    @Test
    fun startInclusiveIsInsideRange() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2026, 8, 7)
        val bounds = ArchiveDayRange.bounds(day.toEpochDay(), zone)
        assertTrue(bounds.startInclusiveEpochMillis >= bounds.startInclusiveEpochMillis)
        assertTrue(bounds.startInclusiveEpochMillis < bounds.endExclusiveEpochMillis)
        assertFalse(bounds.endExclusiveEpochMillis <= bounds.startInclusiveEpochMillis)
    }

    @Test
    fun endExclusiveIsOutsideRange() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2026, 8, 7)
        val bounds = ArchiveDayRange.bounds(day.toEpochDay(), zone)
        assertEquals(bounds.endExclusiveEpochMillis, bounds.endExclusiveEpochMillis)
        assertTrue(bounds.endExclusiveEpochMillis > bounds.startInclusiveEpochMillis)
    }

    @Test
    fun millisecondBeforeEndIsInside() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2026, 8, 7)
        val bounds = ArchiveDayRange.bounds(day.toEpochDay(), zone)
        val lastIncluded = bounds.endExclusiveEpochMillis - 1
        assertTrue(lastIncluded >= bounds.startInclusiveEpochMillis)
        assertTrue(lastIncluded < bounds.endExclusiveEpochMillis)
    }

    @Test
    fun neighborDayDoesNotOverlap() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2026, 8, 7)
        val nextDay = day.plusDays(1)
        val first = ArchiveDayRange.bounds(day.toEpochDay(), zone)
        val second = ArchiveDayRange.bounds(nextDay.toEpochDay(), zone)
        assertEquals(first.endExclusiveEpochMillis, second.startInclusiveEpochMillis)
    }

    @Test
    fun dstSpringForwardDayUsesZoneBoundariesNotFixed24Hours() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 8)
        val bounds = ArchiveDayRange.bounds(day.toEpochDay(), zone)
        val duration = bounds.endExclusiveEpochMillis - bounds.startInclusiveEpochMillis
        assertEquals(23L * 60 * 60 * 1000, duration)
        val noon = ZonedDateTime.of(2026, 3, 8, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(noon >= bounds.startInclusiveEpochMillis)
        assertTrue(noon < bounds.endExclusiveEpochMillis)
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
