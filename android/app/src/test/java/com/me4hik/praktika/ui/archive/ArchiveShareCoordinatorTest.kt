// 07.08.2026 Stage 21 Share cursor by Me4Hik START - unit tests ArchiveShareCoordinator
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter
import com.me4hik.praktika.export.share.EntryShareMode
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

class ArchiveShareCoordinatorTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private lateinit var shareRoot: File
    private lateinit var tempFileStore: ShareTempFileStore

    @Before
    fun setUp() {
        shareRoot = File.createTempFile("share-coordinator-test", null).apply {
            delete()
            mkdir()
        }
        tempFileStore = ShareTempFileStore(shareRoot = shareRoot)
    }

    @Test
    fun shareEntryEmitsQuestionOnlyText() = runBlocking {
        val coordinator = createCoordinator()
        val eventDeferred = async { coordinator.events.first() }
        coordinator.shareEntry(
            mode = EntryShareMode.QUESTION_ONLY,
            questionText = "Snapshot Q",
            answerText = "Hidden answer",
        )
        val event = eventDeferred.await() as ArchiveShareUiEvent.ShareText
        assertEquals("Snapshot Q", event.payload)
    }

    @Test
    fun shareEntryEmitsQuestionAndAnswerText() = runBlocking {
        val coordinator = createCoordinator()
        val eventDeferred = async { coordinator.events.first() }
        coordinator.shareEntry(
            mode = EntryShareMode.QUESTION_AND_ANSWER,
            questionText = "Snapshot Q",
            answerText = "Visible answer",
        )
        val event = eventDeferred.await() as ArchiveShareUiEvent.ShareText
        assertEquals("Snapshot Q\n\nVisible answer", event.payload)
    }

    @Test
    fun emptySelectionEmitsNoAnswers() = runBlocking {
        val coordinator = createCoordinator(
            useCase = ArchiveExportUseCase(
                FakeArchiveReadRepository(),
                FixedArchiveZoneIdProvider(zone),
            ),
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestFileShare(ExportSelection.All, ExportFormat.MARKDOWN)
        assertEquals(ArchiveShareUiEvent.NoAnswers, eventDeferred.await())
    }

    @Test
    fun markdownShareReusesExportDocumentBytes() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone))
        val expected = useCase.prepare(ExportSelection.All, ExportFormat.MARKDOWN)
        val coordinator = createCoordinator(useCase = useCase)
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestFileShare(ExportSelection.All, ExportFormat.MARKDOWN)
        val event = eventDeferred.await() as ArchiveShareUiEvent.ShareFile
        assertEquals("praktika-all.md", event.suggestedFileName)
        assertEquals(MarkdownArchiveFormatter.MIME_TYPE, event.mimeType)
        assertArrayEquals(
            (expected as com.me4hik.praktika.export.ArchiveExportPrepareResult.Ready).document.bytes,
            event.file.readBytes(),
        )
    }

    @Test
    fun csvShareReusesExportDocumentBytesWithBom() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val useCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone))
        val expected = useCase.prepare(ExportSelection.All, ExportFormat.CSV)
        val coordinator = createCoordinator(useCase = useCase)
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestFileShare(ExportSelection.All, ExportFormat.CSV)
        val event = eventDeferred.await() as ArchiveShareUiEvent.ShareFile
        assertEquals("praktika-all.csv", event.suggestedFileName)
        assertEquals(CsvArchiveFormatter.MIME_TYPE, event.mimeType)
        assertArrayEquals(
            (expected as com.me4hik.praktika.export.ArchiveExportPrepareResult.Ready).document.bytes,
            event.file.readBytes(),
        )
    }

    @Test
    fun failingTempStoreEmitsPrepareFailed() = runBlocking {
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))
        val blockedRoot = File.createTempFile("blocked-share-root", null)
        blockedRoot.deleteOnExit()
        if (blockedRoot.exists()) {
            blockedRoot.delete()
        }
        check(blockedRoot.createNewFile())
        val coordinator = ArchiveShareCoordinator(
            exportUseCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
            tempFileStore = ShareTempFileStore(shareRoot = blockedRoot),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val eventDeferred = async { coordinator.events.first() }
        coordinator.requestFileShare(ExportSelection.All, ExportFormat.MARKDOWN)
        assertEquals(ArchiveShareUiEvent.PrepareFailed, eventDeferred.await())
    }

    private fun createCoordinator(
        useCase: ArchiveExportUseCase = ArchiveExportUseCase(
            FakeArchiveReadRepository(),
            FixedArchiveZoneIdProvider(zone),
        ),
    ): ArchiveShareCoordinator {
        return ArchiveShareCoordinator(
            exportUseCase = useCase,
            tempFileStore = tempFileStore,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
