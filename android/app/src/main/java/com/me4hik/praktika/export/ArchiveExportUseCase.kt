// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - snapshot и подготовка Markdown export
package com.me4hik.praktika.export

import com.me4hik.praktika.data.read.ArchiveReadRepository
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter
import com.me4hik.praktika.export.pdf.PdfArchiveFormatter
import com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter
import com.me4hik.praktika.ui.archive.ArchiveDayRange
import com.me4hik.praktika.ui.archive.ArchiveZoneIdProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ArchiveExportUseCase(
    private val archiveReadRepository: ArchiveReadRepository,
    private val zoneIdProvider: ArchiveZoneIdProvider,
    private val markdownFormatter: MarkdownArchiveFormatter = MarkdownArchiveFormatter(),
    private val csvFormatter: CsvArchiveFormatter = CsvArchiveFormatter(),
    private val pdfFormatter: PdfArchiveFormatter = PdfArchiveFormatter(),
    // 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START
    private val xlsxFormatter: XlsxArchiveFormatter = XlsxArchiveFormatter(),
    // 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun prepare(
        selection: ExportSelection,
        format: ExportFormat,
    ): ArchiveExportPrepareResult {
        return withContext(ioDispatcher) {
            val zoneId = zoneIdProvider.currentZoneId()
            val entries = snapshot(selection, zoneId)
            if (entries.isEmpty()) {
                ArchiveExportPrepareResult.NoAnswers
            } else {
                val document = when (format) {
                    ExportFormat.MARKDOWN -> markdownFormatter.format(selection, entries, zoneId)
                    ExportFormat.CSV -> csvFormatter.format(selection, entries, zoneId)
                    ExportFormat.PDF -> pdfFormatter.format(selection, entries, zoneId)
                    // 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START
                    ExportFormat.XLSX -> xlsxFormatter.format(selection, entries, zoneId)
                    // 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END
                }
                ArchiveExportPrepareResult.Ready(document = document)
            }
        }
    }

    private suspend fun snapshot(
        selection: ExportSelection,
        zoneId: java.time.ZoneId,
    ): List<com.me4hik.praktika.data.read.ArchiveEntry> {
        return when (selection) {
            ExportSelection.All -> archiveReadRepository.observeEntries().first()
            is ExportSelection.Day -> {
                val bounds = ArchiveDayRange.bounds(selection.epochDay, zoneId)
                archiveReadRepository.observeEntriesInRange(
                    bounds.startInclusiveEpochMillis,
                    bounds.endExclusiveEpochMillis,
                ).first()
            }
            is ExportSelection.Range -> {
                val bounds = ArchivePeriodRange.bounds(
                    selection.startEpochDay,
                    selection.endEpochDayInclusive,
                    zoneId,
                )
                archiveReadRepository.observeEntriesInRange(
                    bounds.startInclusiveEpochMillis,
                    bounds.endExclusiveEpochMillis,
                ).first()
            }
            is ExportSelection.Question -> {
                archiveReadRepository.observeEntriesForQuestion(selection.questionId).first()
            }
        }
    }
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
