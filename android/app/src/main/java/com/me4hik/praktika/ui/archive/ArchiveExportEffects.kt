// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - Compose launcher и snackbar export
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - MIME-aware CreateDocument launchers
package com.me4hik.praktika.ui.archive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.me4hik.praktika.export.csv.CsvArchiveFormatter

@Composable
fun ArchiveExportEffects(
    coordinator: ArchiveExportCoordinator,
    snackbarHostState: SnackbarHostState,
    noAnswersMessage: String,
    successMessage: String,
    writeErrorMessage: String,
) {
    val markdownCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter.MIME_TYPE,
        ),
    ) { uri ->
        coordinator.onCreateDocumentResult(uri)
    }
    val csvCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CsvArchiveFormatter.MIME_TYPE),
    ) { uri ->
        coordinator.onCreateDocumentResult(uri)
    }

    LaunchedEffect(coordinator, snackbarHostState) {
        coordinator.events.collect { event ->
            when (event) {
                is ArchiveExportUiEvent.RequestCreateDocument -> {
                    when (event.mimeType) {
                        CsvArchiveFormatter.MIME_TYPE -> {
                            csvCreateDocumentLauncher.launch(event.suggestedFileName)
                        }
                        else -> {
                            markdownCreateDocumentLauncher.launch(event.suggestedFileName)
                        }
                    }
                }
                ArchiveExportUiEvent.NoAnswers -> {
                    snackbarHostState.showSnackbar(noAnswersMessage)
                }
                ArchiveExportUiEvent.Success -> {
                    snackbarHostState.showSnackbar(successMessage)
                }
                ArchiveExportUiEvent.WriteFailed -> {
                    snackbarHostState.showSnackbar(writeErrorMessage)
                }
            }
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
