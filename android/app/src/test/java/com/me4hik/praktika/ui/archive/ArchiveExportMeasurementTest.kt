package com.me4hik.praktika.ui.archive

import android.net.Uri
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.measurement.AnalyticsEventNames
import com.me4hik.praktika.measurement.FakeAnalyticsTracker
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ArchiveExportMeasurementTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun successfulWrite_emitsArchiveExported_failedWrite_emitsNone() = runBlocking {
        val tracker = FakeAnalyticsTracker()
        val repository = FakeArchiveReadRepository()
        repository.emit(listOf(sampleArchiveEntry(1, 1_000L)))

        val successCoordinator = ArchiveExportCoordinator(
            exportUseCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
            documentWriter = RecordingExportDocumentWriter(shouldFail = false),
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            analyticsTracker = tracker,
        )
        val prepareEvent = async { successCoordinator.events.first() }
        successCoordinator.requestExport(ExportSelection.All, ExportFormat.MARKDOWN)
        prepareEvent.await()
        assertEquals(0, tracker.count(AnalyticsEventNames.ARCHIVE_EXPORTED))

        val successEvent = async { successCoordinator.events.first() }
        successCoordinator.onCreateDocumentResult(Uri.parse("content://test/export.md"))
        successEvent.await()
        assertEquals(1, tracker.count(AnalyticsEventNames.ARCHIVE_EXPORTED))
        assertEquals(
            "markdown",
            tracker.recorded().single().params.asMap()["export_format"],
        )

        val failCoordinator = ArchiveExportCoordinator(
            exportUseCase = ArchiveExportUseCase(repository, FixedArchiveZoneIdProvider(zone)),
            documentWriter = RecordingExportDocumentWriter(shouldFail = true),
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            analyticsTracker = tracker,
        )
        val failPrepare = async { failCoordinator.events.first() }
        failCoordinator.requestExport(ExportSelection.All, ExportFormat.CSV)
        failPrepare.await()
        val failWrite = async { failCoordinator.events.first() }
        failCoordinator.onCreateDocumentResult(Uri.parse("content://test/export.csv"))
        failWrite.await()
        assertEquals(1, tracker.count(AnalyticsEventNames.ARCHIVE_EXPORTED))
    }
}
