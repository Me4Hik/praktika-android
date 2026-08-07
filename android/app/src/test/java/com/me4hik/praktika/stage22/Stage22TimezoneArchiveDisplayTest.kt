// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - timezone archive/export cross-feature
package com.me4hik.praktika.stage22

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter
import com.me4hik.praktika.ui.archive.ArchiveDisplayFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class Stage22TimezoneArchiveDisplayTest {
    private val displayFormatter = ArchiveDisplayFormatter()
    private val markdownFormatter = MarkdownArchiveFormatter(displayFormatter)
    private val csvFormatter = CsvArchiveFormatter()

    @Test
    fun archiveDisplayAndExportFollowCurrentZoneIdWithoutMutatingEpoch() {
        val zoneA = ZoneId.of("UTC")
        val zoneB = ZoneId.of("Pacific/Auckland")
        val answeredAtEpochMillis = ZonedDateTime.of(2026, 8, 7, 23, 30, 0, 0, zoneA)
            .toInstant()
            .toEpochMilli()

        val entry = sampleEntry(answeredAtEpochMillis)

        val dateA = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(answeredAtEpochMillis),
            zoneA,
        )
        val timeA = LocalTime.ofInstant(
            java.time.Instant.ofEpochMilli(answeredAtEpochMillis),
            zoneA,
        )
        val dateB = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(answeredAtEpochMillis),
            zoneB,
        )
        val timeB = LocalTime.ofInstant(
            java.time.Instant.ofEpochMilli(answeredAtEpochMillis),
            zoneB,
        )

        assertEquals(LocalDate.of(2026, 8, 7), dateA)
        assertEquals(LocalTime.of(23, 30), timeA)
        assertEquals(LocalDate.of(2026, 8, 8), dateB)
        assertNotEquals(dateA, dateB)

        assertEquals(
            displayFormatter.formatDate(answeredAtEpochMillis, zoneA),
            displayFormatter.formatDate(answeredAtEpochMillis, zoneA),
        )
        assertNotEquals(
            displayFormatter.formatDate(answeredAtEpochMillis, zoneA),
            displayFormatter.formatDate(answeredAtEpochMillis, zoneB),
        )
        assertNotEquals(
            displayFormatter.formatTime(answeredAtEpochMillis, zoneA),
            displayFormatter.formatTime(answeredAtEpochMillis, zoneB),
        )

        val markdownB = markdownFormatter.format(ExportSelection.All, listOf(entry), zoneB)
            .textContent()
        assertTrue(markdownB.contains(displayFormatter.formatDate(answeredAtEpochMillis, zoneB)))
        assertTrue(markdownB.contains(displayFormatter.formatTime(answeredAtEpochMillis, zoneB)))
        assertTrue(markdownB.contains("11:30"))

        val csvB = csvFormatter.format(ExportSelection.All, listOf(entry), zoneB)
            .textWithoutBom()
        assertTrue(csvB.contains("2026-08-08"))
        assertTrue(csvB.contains("11:30"))

        assertEquals(answeredAtEpochMillis, entry.answeredAtEpochMillis)
    }

    @Test
    fun dstSafeSummerInstantDiffersBetweenFixedZones() {
        val zoneA = ZoneId.of("America/Phoenix")
        val zoneB = ZoneId.of("America/New_York")
        val answeredAtEpochMillis = ZonedDateTime.of(2026, 7, 15, 18, 0, 0, 0, zoneB)
            .toInstant()
            .toEpochMilli()

        val entry = sampleEntry(answeredAtEpochMillis)

        val phoenixTime = displayFormatter.formatTime(answeredAtEpochMillis, zoneA)
        val newYorkTime = displayFormatter.formatTime(answeredAtEpochMillis, zoneB)

        assertEquals("15:00", phoenixTime)
        assertEquals("18:00", newYorkTime)
        assertNotEquals(phoenixTime, newYorkTime)

        val csvPhoenix = csvFormatter.format(ExportSelection.All, listOf(entry), zoneA)
            .textWithoutBom()
        val csvNewYork = csvFormatter.format(ExportSelection.All, listOf(entry), zoneB)
            .textWithoutBom()

        assertTrue(csvPhoenix.contains("15:00"))
        assertTrue(csvNewYork.contains("18:00"))
        assertEquals(answeredAtEpochMillis, entry.answeredAtEpochMillis)
    }

    private fun sampleEntry(answeredAtEpochMillis: Long) = ArchiveEntry(
        answerId = 1L,
        occurrenceId = 10L,
        questionId = 1,
        questionText = "Snapshot question",
        answerText = "Answer text",
        answeredAtEpochMillis = answeredAtEpochMillis,
        plannedAtEpochMillis = answeredAtEpochMillis - 3_600_000L,
        cycleNumber = 1,
        cyclePosition = 1,
    )

    private fun com.me4hik.praktika.export.ExportDocument.textContent(): String =
        String(bytes, Charsets.UTF_8)

    private fun com.me4hik.praktika.export.ExportDocument.textWithoutBom(): String {
        val bomLength = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        return String(bytes, bomLength, bytes.size - bomLength, Charsets.UTF_8)
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END
