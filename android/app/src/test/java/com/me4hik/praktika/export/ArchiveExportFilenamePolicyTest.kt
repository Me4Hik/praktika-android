// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - unit tests filename policy
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - CSV filenames
package com.me4hik.praktika.export

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExportFilenamePolicyTest {
    @Test
    fun allFilename() {
        assertEquals(
            "praktika-all.md",
            ArchiveExportFilenamePolicy.suggestedFileName(ExportSelection.All, ExportFormat.MARKDOWN),
        )
        assertEquals(
            "praktika-all.csv",
            ArchiveExportFilenamePolicy.suggestedFileName(ExportSelection.All, ExportFormat.CSV),
        )
    }

    @Test
    fun dayFilenameUsesIsoDate() {
        val day = LocalDate.of(2026, 8, 7).toEpochDay()
        assertEquals(
            "praktika-day-2026-08-07.md",
            ArchiveExportFilenamePolicy.suggestedFileName(ExportSelection.Day(day), ExportFormat.MARKDOWN),
        )
        assertEquals(
            "praktika-day-2026-08-07.csv",
            ArchiveExportFilenamePolicy.suggestedFileName(ExportSelection.Day(day), ExportFormat.CSV),
        )
    }

    @Test
    fun rangeFilenameUsesIsoDates() {
        val start = LocalDate.of(2026, 8, 1).toEpochDay()
        val end = LocalDate.of(2026, 8, 7).toEpochDay()
        assertEquals(
            "praktika-period-2026-08-01_2026-08-07.md",
            ArchiveExportFilenamePolicy.suggestedFileName(
                ExportSelection.Range(start, end),
                ExportFormat.MARKDOWN,
            ),
        )
        assertEquals(
            "praktika-period-2026-08-01_2026-08-07.csv",
            ArchiveExportFilenamePolicy.suggestedFileName(
                ExportSelection.Range(start, end),
                ExportFormat.CSV,
            ),
        )
    }

    @Test
    fun questionFilenameUsesPaddedId() {
        assertEquals(
            "praktika-question-01.md",
            ArchiveExportFilenamePolicy.suggestedFileName(ExportSelection.Question(1), ExportFormat.MARKDOWN),
        )
        assertEquals(
            "praktika-question-12.csv",
            ArchiveExportFilenamePolicy.suggestedFileName(ExportSelection.Question(12), ExportFormat.CSV),
        )
    }

    @Test
    fun filenamesAreAsciiAndLocaleIndependent() {
        Locale.setDefault(Locale.FRANCE)
        val filename = ArchiveExportFilenamePolicy.suggestedFileName(
            ExportSelection.Day(LocalDate.of(2026, 8, 7).toEpochDay()),
            ExportFormat.CSV,
        )
        assertTrue(filename.all { it.code <= 127 })
        assertTrue(filename.endsWith(".csv"))
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
