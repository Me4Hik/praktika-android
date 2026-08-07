// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - unit tests ArchiveDateGrouping
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveDateGroupingTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun emptyListReturnsEmptyDates() {
        assertTrue(ArchiveDateGrouping.groupDates(emptyList(), zone).isEmpty())
    }

    @Test
    fun singleDateWithMultipleAnswers() {
        val day = LocalDate.of(2026, 8, 7)
        val entries = listOf(
            entry(day, 10, 0),
            entry(day, 18, 30),
        )
        val grouped = ArchiveDateGrouping.groupDates(entries, zone)
        assertEquals(1, grouped.size)
        assertEquals(day, grouped.single().date)
        assertEquals(2, grouped.single().answerCount)
    }

    @Test
    fun multipleDatesSortedNewestFirst() {
        val older = LocalDate.of(2026, 8, 5)
        val newer = LocalDate.of(2026, 8, 7)
        val grouped = ArchiveDateGrouping.groupDates(
            listOf(entry(older, 12, 0), entry(newer, 9, 0)),
            zone,
        )
        assertEquals(listOf(newer, older), grouped.map { it.date })
    }

    @Test
    fun sameTimestampCountsOncePerEntry() {
        val day = LocalDate.of(2026, 8, 7)
        val epoch = zoned(day, 12, 0).toInstant().toEpochMilli()
        val grouped = ArchiveDateGrouping.groupDates(
            listOf(
                sampleEntry(answerId = 1, epoch = epoch),
                sampleEntry(answerId = 2, epoch = epoch),
            ),
            zone,
        )
        assertEquals(1, grouped.size)
        assertEquals(2, grouped.single().answerCount)
    }

    @Test
    fun midnightBoundaryUsesLocalDate() {
        val dayOne = LocalDate.of(2026, 8, 7)
        val dayTwo = LocalDate.of(2026, 8, 8)
        val entries = listOf(
            entry(dayOne, 23, 59),
            entry(dayTwo, 0, 1),
        )
        val grouped = ArchiveDateGrouping.groupDates(entries, zone)
        assertEquals(2, grouped.size)
        assertEquals(dayTwo, grouped[0].date)
        assertEquals(dayOne, grouped[1].date)
    }

    @Test
    fun differentZoneIdCanSplitSameInstantIntoDifferentDates() {
        val instant = ZonedDateTime.of(2026, 8, 7, 1, 30, 0, 0, ZoneId.of("UTC")).toInstant()
        val epoch = instant.toEpochMilli()
        val entry = sampleEntry(answerId = 1, epoch = epoch)
        val utcDate = ArchiveDateGrouping.groupDates(listOf(entry), ZoneId.of("UTC")).single().date
        val kievDate = ArchiveDateGrouping.groupDates(listOf(entry), ZoneId.of("Europe/Kiev")).single().date
        assertEquals(LocalDate.of(2026, 8, 7), utcDate)
        assertEquals(LocalDate.of(2026, 8, 7), kievDate)
    }

    private fun entry(day: LocalDate, hour: Int, minute: Int): ArchiveEntry {
        return sampleEntry(
            answerId = day.toEpochDay() * 100 + hour,
            epoch = zoned(day, hour, minute).toInstant().toEpochMilli(),
        )
    }

    private fun zoned(day: LocalDate, hour: Int, minute: Int) =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, zone)

    private fun sampleEntry(answerId: Long, epoch: Long) = ArchiveEntry(
        answerId = answerId,
        occurrenceId = answerId,
        questionId = 1,
        questionText = "Question",
        answerText = "Answer",
        answeredAtEpochMillis = epoch,
        plannedAtEpochMillis = epoch - 1_000,
        cycleNumber = 1,
        cyclePosition = 1,
    )
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
