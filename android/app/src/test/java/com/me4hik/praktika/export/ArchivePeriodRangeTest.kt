// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - unit tests ArchivePeriodRange
package com.me4hik.praktika.export

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePeriodRangeTest {
    @Test
    fun startDayInclusiveAndEndDayInclusiveForUx() {
        val zone = ZoneId.of("Europe/Moscow")
        val start = LocalDate.of(2026, 8, 1).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        val bounds = ArchivePeriodRange.bounds(start, end, zone)
        val firstAnswer = ZonedDateTime.of(2026, 8, 1, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val lastAnswer = ZonedDateTime.of(2026, 8, 7, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(firstAnswer >= bounds.startInclusiveEpochMillis)
        assertTrue(firstAnswer < bounds.endExclusiveEpochMillis)
        assertTrue(lastAnswer >= bounds.startInclusiveEpochMillis)
        assertTrue(lastAnswer < bounds.endExclusiveEpochMillis)
    }

    @Test
    fun nextDayAfterEndExcluded() {
        val zone = ZoneId.of("Europe/Moscow")
        val start = LocalDate.of(2026, 8, 1).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        val bounds = ArchivePeriodRange.bounds(start, end, zone)
        val nextDayStart = LocalDate.of(2026, 8, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(nextDayStart, bounds.endExclusiveEpochMillis)
    }

    @Test
    fun dstSpringForwardRangeUsesZoneBoundaries() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 3, 8).toEpochDay()
        val bounds = ArchivePeriodRange.bounds(day, day, zone)
        val duration = bounds.endExclusiveEpochMillis - bounds.startInclusiveEpochMillis
        assertEquals(23L * 60 * 60 * 1000, duration)
    }
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
