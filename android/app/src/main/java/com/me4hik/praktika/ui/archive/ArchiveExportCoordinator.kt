// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - orchestration export без Activity launcher
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - format-aware export orchestration
package com.me4hik.praktika.ui.archive

import android.net.Uri
import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.ExportDocumentWriter
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ArchiveExportState {
    data object Idle : ArchiveExportState
    data object Preparing : ArchiveExportState
    data class AwaitingDestination(
        val document: ExportDocument,
    ) : ArchiveExportState
    data object Writing : ArchiveExportState
}

sealed interface ArchiveExportUiEvent {
    data class RequestCreateDocument(
        val suggestedFileName: String,
        val mimeType: String,
    ) : ArchiveExportUiEvent

    data object NoAnswers : ArchiveExportUiEvent
    data object Success : ArchiveExportUiEvent
    data object WriteFailed : ArchiveExportUiEvent
}

class ArchiveExportCoordinator(
    private val exportUseCase: ArchiveExportUseCase,
    private val documentWriter: ExportDocumentWriter,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<ArchiveExportState>(ArchiveExportState.Idle)
    val state: StateFlow<ArchiveExportState> = _state.asStateFlow()

    private val eventsChannel = Channel<ArchiveExportUiEvent>(capacity = Channel.BUFFERED)
    val events: Flow<ArchiveExportUiEvent> = eventsChannel.receiveAsFlow()

    fun requestExport(selection: ExportSelection, format: ExportFormat) {
        if (_state.value != ArchiveExportState.Idle) {
            return
        }
        scope.launch {
            _state.value = ArchiveExportState.Preparing
            when (val result = exportUseCase.prepare(selection, format)) {
                ArchiveExportPrepareResult.NoAnswers -> {
                    _state.value = ArchiveExportState.Idle
                    eventsChannel.send(ArchiveExportUiEvent.NoAnswers)
                }
                is ArchiveExportPrepareResult.Ready -> {
                    _state.value = ArchiveExportState.AwaitingDestination(result.document)
                    eventsChannel.send(
                        ArchiveExportUiEvent.RequestCreateDocument(
                            suggestedFileName = result.document.suggestedFileName,
                            mimeType = result.document.mimeType,
                        ),
                    )
                }
            }
        }
    }

    fun onCreateDocumentResult(uri: Uri?) {
        if (uri == null) {
            if (_state.value is ArchiveExportState.AwaitingDestination) {
                _state.value = ArchiveExportState.Idle
            }
            return
        }
        val document = (_state.value as? ArchiveExportState.AwaitingDestination)?.document ?: return
        scope.launch {
            _state.value = ArchiveExportState.Writing
            try {
                withContext(ioDispatcher) {
                    documentWriter.write(uri, document.bytes)
                }
                _state.value = ArchiveExportState.Idle
                eventsChannel.send(ArchiveExportUiEvent.Success)
            } catch (_: Exception) {
                _state.value = ArchiveExportState.Idle
                eventsChannel.send(ArchiveExportUiEvent.WriteFailed)
            }
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
