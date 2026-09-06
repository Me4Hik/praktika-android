// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START - XLSX export/share wiring host tests
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter
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

class XlsxArchiveExportShareWiringHostTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private lateinit var shareRoot: File

    @Before
    fun setUp() {
        shareRoot = File.createTempFile("xlsx-share-wiring", null).apply {
            delete()
            mkdir()
        }
    }

    @Test
    fun exportXlsx_requestsCreateDocumentWithXlsxMimeAndFilename() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val coordinator = ArchiveExportCoordinator(
            exportUseCase = ArchiveExportUseCase(
                archiveReadRepository = repository,
                zoneIdProvider = FixedArchiveZoneIdProvider(zone),
            ),
            documentWriter = RecordingExportDocumentWriter(shouldFail = false),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestExport(ExportSelection.All, ExportFormat.XLSX)
        val event = eventDeferred.await() as ArchiveExportUiEvent.RequestCreateDocument
        assertEquals("praktika-all.xlsx", event.suggestedFileName)
        assertEquals(XlsxArchiveFormatter.MIME_TYPE, event.mimeType)
        val awaiting = coordinator.state.value as ArchiveExportState.AwaitingDestination
        assertEquals('P'.code.toByte(), awaiting.document.bytes[0])
        assertEquals('K'.code.toByte(), awaiting.document.bytes[1])
    }

    @Test
    fun shareXlsx_emitsXlsxMimeFilenameAndExactBytes() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(
            listOf(
                sampleArchiveEntry(
                    answerId = 2,
                    epochMillis = 2_000L,
                    questionText = "Кириллица",
                    answerText = "emoji 🎉\nline2",
                ),
            ),
        )
        val useCase = ArchiveExportUseCase(
            archiveReadRepository = repository,
            zoneIdProvider = FixedArchiveZoneIdProvider(zone),
        )
        val expected = useCase.prepare(ExportSelection.All, ExportFormat.XLSX)
            as ArchiveExportPrepareResult.Ready
        val coordinator = ArchiveShareCoordinator(
            exportUseCase = useCase,
            tempFileStore = ShareTempFileStore(shareRoot = shareRoot),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestFileShare(ExportSelection.All, ExportFormat.XLSX)
        val event = eventDeferred.await() as ArchiveShareUiEvent.ShareFile
        assertEquals("praktika-all.xlsx", event.suggestedFileName)
        assertEquals(XlsxArchiveFormatter.MIME_TYPE, event.mimeType)
        assertTrue(event.suggestedFileName.endsWith(".xlsx"))
        assertArrayEquals(expected.document.bytes, event.file.readBytes())
    }
}
// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END
