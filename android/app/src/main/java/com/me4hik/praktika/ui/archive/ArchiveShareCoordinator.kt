// 07.08.2026 Stage 21 Share cursor by Me4Hik START - orchestration share без export coordinator
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.export.ArchiveExportPrepareResult
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.share.ArchiveShareTextFormatter
import com.me4hik.praktika.export.share.EntryShareMode
import com.me4hik.praktika.share.ShareTempFileStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ArchiveShareUiEvent {
    data class ShareText(
        val payload: String,
    ) : ArchiveShareUiEvent

    data class ShareFile(
        val file: File,
        val mimeType: String,
        val suggestedFileName: String,
    ) : ArchiveShareUiEvent

    data object NoAnswers : ArchiveShareUiEvent

    data object PrepareFailed : ArchiveShareUiEvent
}

class ArchiveShareCoordinator(
    private val exportUseCase: ArchiveExportUseCase,
    private val textFormatter: ArchiveShareTextFormatter = ArchiveShareTextFormatter(),
    private val tempFileStore: ShareTempFileStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val isBusy = MutableStateFlow(false)
    private val eventsChannel = Channel<ArchiveShareUiEvent>(capacity = Channel.BUFFERED)
    val events: Flow<ArchiveShareUiEvent> = eventsChannel.receiveAsFlow()

    fun shareEntry(
        mode: EntryShareMode,
        questionText: String,
        answerText: String,
    ) {
        if (isBusy.value) {
            return
        }
        scope.launch {
            val payload = textFormatter.format(mode, questionText, answerText)
            eventsChannel.send(ArchiveShareUiEvent.ShareText(payload))
        }
    }

    fun requestFileShare(
        selection: ExportSelection,
        format: ExportFormat,
    ) {
        if (isBusy.value) {
            return
        }
        scope.launch {
            isBusy.value = true
            try {
                when (val result = exportUseCase.prepare(selection, format)) {
                    ArchiveExportPrepareResult.NoAnswers -> {
                        eventsChannel.send(ArchiveShareUiEvent.NoAnswers)
                    }
                    is ArchiveExportPrepareResult.Ready -> {
                        try {
                            val file = withContext(ioDispatcher) {
                                tempFileStore.write(result.document)
                            }
                            eventsChannel.send(
                                ArchiveShareUiEvent.ShareFile(
                                    file = file,
                                    mimeType = result.document.mimeType,
                                    suggestedFileName = result.document.suggestedFileName,
                                ),
                            )
                        } catch (_: Exception) {
                            eventsChannel.send(ArchiveShareUiEvent.PrepareFailed)
                        }
                    }
                }
            } finally {
                isBusy.value = false
            }
        }
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
