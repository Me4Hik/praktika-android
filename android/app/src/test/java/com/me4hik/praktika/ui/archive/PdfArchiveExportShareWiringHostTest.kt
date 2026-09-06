// PROMPT 180 — PDF export/share wiring MIME + filename (no PdfDocument on host JVM)
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.read.ArchiveEntry
import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.pdf.PdfArchiveFormatter
import com.me4hik.praktika.share.ShareTempFileStore
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfArchiveExportShareWiringHostTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private lateinit var shareRoot: File
    private val fixedPdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-

    @Before
    fun setUp() {
        shareRoot = File.createTempFile("pdf-share-wiring", null).apply {
            delete()
            mkdir()
        }
    }

    @Test
    fun exportPdf_requestsCreateDocumentWithPdfMimeAndFilename() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val coordinator = ArchiveExportCoordinator(
            exportUseCase = ArchiveExportUseCase(
                archiveReadRepository = repository,
                zoneIdProvider = FixedArchiveZoneIdProvider(zone),
                pdfFormatter = FixedPdfArchiveFormatter(fixedPdfBytes),
            ),
            documentWriter = RecordingExportDocumentWriter(shouldFail = false),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.PDF)
        val event = eventDeferred.await() as ArchiveExportUiEvent.RequestCreateDocument
        assertEquals("praktika-all.pdf", event.suggestedFileName)
        assertEquals(PdfArchiveFormatter.MIME_TYPE, event.mimeType)
        val awaiting = coordinator.state.value as ArchiveExportState.AwaitingDestination
        assertArrayEquals(fixedPdfBytes, awaiting.document.bytes)
    }

    @Test
    fun sharePdf_emitsPdfMimeFilenameAndExactBytes() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(2, 2_000L)))
        val useCase = ArchiveExportUseCase(
            archiveReadRepository = repository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            pdfFormatter = FixedPdfArchiveFormatter(fixedPdfBytes),
        )
        val expected = useCase.prepare(ExportSelection.All, ExportFormat.PDF)
            as ArchiveExportPrepareResult.Ready
        val coordinator = ArchiveShareCoordinator(
            exportUseCase = useCase,
            tempFileStore = ShareTempFileStore(shareRoot = shareRoot),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestFileShare(ExportSelection.All, ExportFormat.PDF)
        val event = eventDeferred.await() as ArchiveShareUiEvent.ShareFile
        assertEquals("praktika-all.pdf", event.suggestedFileName)
        assertEquals(PdfArchiveFormatter.MIME_TYPE, event.mimeType)
        assertTrue(event.suggestedFileName.endsWith(".pdf"))
        assertArrayEquals(expected.document.bytes, event.file.readBytes())
        assertArrayEquals(fixedPdfBytes, event.file.readBytes())
    }

    private class FixedPdfArchiveFormatter(
        private val bytes: ByteArray,
    ) : PdfArchiveFormatter() {
        override fun format(
            selection: ExportSelection,
            entries: List<ArchiveEntry>,
            zoneId: ZoneId,
        ): ExportDocument {
            return ExportDocument(
                suggestedFileName = com.me4hik.praktika.export.ArchiveExportFilenamePolicy
                    .suggestedFileName(selection, ExportFormat.PDF),
                mimeType = MIME_TYPE,
                bytes = bytes,
            )
        }
    }
}
