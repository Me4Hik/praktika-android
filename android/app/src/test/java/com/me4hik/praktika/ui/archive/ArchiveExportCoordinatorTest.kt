// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - unit tests ArchiveExportCoordinator
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - CSV coordinator coverage
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExportCoordinatorTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun emptySelectionEmitsNoAnswers() = runBlocking {
        val coordinator = createCoordinator(
            useCase = ArchiveExportUseCase(
                FakeArchiveReadRepository(),
                FixedArchiveZoneIdProvider(zone),
            ),
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.MARKDOWN)
        assertEquals(ArchiveExportUiEvent.NoAnswers, eventDeferred.await())
        assertTrue(coordinator.state.value is ArchiveExportState.Idle)
    }

    @Test
    fun markdownRequestPreparesMdDocument() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val coordinator = createCoordinator(
            useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.MARKDOWN)
        val event = eventDeferred.await() as ArchiveExportUiEvent.RequestCreateDocument
        assertEquals("praktika-all.md", event.suggestedFileName)
        assertEquals(MarkdownArchiveFormatter.MIME_TYPE, event.mimeType)
        assertTrue(coordinator.state.value is ArchiveExportState.AwaitingDestination)
    }

    @Test
    fun csvRequestPreparesCsvDocument() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val coordinator = createCoordinator(
            useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.CSV)
        val event = eventDeferred.await() as ArchiveExportUiEvent.RequestCreateDocument
        assertEquals("praktika-all.csv", event.suggestedFileName)
        assertEquals(CsvArchiveFormatter.MIME_TYPE, event.mimeType)
    }

    @Test
    fun markdownAndCsvMimeRemainDistinctFromPdf() {
        assertEquals("text/markdown", MarkdownArchiveFormatter.MIME_TYPE)
        assertEquals("text/csv", CsvArchiveFormatter.MIME_TYPE)
        assertEquals("application/pdf", com.me4hik.praktika.export.pdf.PdfArchiveFormatter.MIME_TYPE)
    }

    // 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START
    @Test
    fun xlsxRequestPreparesXlsxDocument() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val coordinator = createCoordinator(
            useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.XLSX)
        val event = eventDeferred.await() as ArchiveExportUiEvent.RequestCreateDocument
        assertEquals("praktika-all.xlsx", event.suggestedFileName)
        assertEquals(
            com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter.MIME_TYPE,
            event.mimeType,
        )
    }

    @Test
    fun xlsxMimeRemainsDistinctFromMarkdownCsvPdf() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter.MIME_TYPE,
        )
        assertTrue(
            com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter.MIME_TYPE
                != MarkdownArchiveFormatter.MIME_TYPE,
        )
        assertTrue(
            com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter.MIME_TYPE
                != CsvArchiveFormatter.MIME_TYPE,
        )
        assertTrue(
            com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter.MIME_TYPE
                != com.me4hik.praktika.export.pdf.PdfArchiveFormatter.MIME_TYPE,
        )
    }
    // 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END

    @Test
    fun csvPrepareViaUseCase_preservesBomMimeFilenameAndCyrillicBytes() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(
            listOf(
                sampleArchiveEntry(
                    answerId = 3,
                    epochMillis = 1_000L,
                    questionText = "Кириллица, \"да\"",
                    answerText = "строка1\nстрока2",
                ),
            ),
        )
        val useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone))
        val prepared = useCase.prepare(ExportSelection.All, ExportFormat.CSV)
            as com.me4hik.praktika.export.ArchiveExportPrepareResult.Ready
        assertEquals(CsvArchiveFormatter.MIME_TYPE, prepared.document.mimeType)
        assertEquals("praktika-all.csv", prepared.document.suggestedFileName)
        assertArrayEquals(CsvArchiveFormatter.UTF8_BOM, prepared.document.bytes.copyOfRange(0, 3))
        val payload = prepared.document.bytes.copyOfRange(3, prepared.document.bytes.size)
        val text = String(payload, Charsets.UTF_8)
        assertArrayEquals(payload, text.toByteArray(Charsets.UTF_8))
        assertTrue(text.contains("Кириллица"))
        assertTrue(text.contains("строка1"))
    }

    @Test
    fun cancelPickerReturnsIdleSilently() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val coordinator = createCoordinator(
            useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
        )
        val firstEvent = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.MARKDOWN)
        firstEvent.await()
        coordinator.onCreateDocumentResult(null)
        assertTrue(coordinator.state.value is ArchiveExportState.Idle)
    }

    private fun createCoordinator(
        useCase: ArchiveExportUseCase,
    ): ArchiveExportCoordinator {
        return ArchiveExportCoordinator(
            exportUseCase = useCase,
            documentWriter = RecordingExportDocumentWriter(shouldFail = false),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
