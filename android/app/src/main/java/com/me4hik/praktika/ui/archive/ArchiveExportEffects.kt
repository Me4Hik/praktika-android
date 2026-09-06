// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - Compose launcher и snackbar export
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - MIME-aware CreateDocument launchers
// PROMPT 180 — explicit PDF CreateDocument launcher (no else→markdown trap)
package com.me4hik.praktika.ui.archive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import com.me4hik.praktika.export.markdown.MarkdownArchiveFormatter
import com.me4hik.praktika.export.pdf.PdfArchiveFormatter
import com.me4hik.praktika.ui.components.showPracticeInfoSnackbar

@Composable
fun ArchiveExportEffects(
    coordinator: ArchiveExportCoordinator,
    snackbarHostState: SnackbarHostState,
    noAnswersMessage: String,
    successMessage: String,
    writeErrorMessage: String,
) {
    val markdownCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MarkdownArchiveFormatter.MIME_TYPE),
    ) { uri ->
        coordinator.onCreateDocumentResult(uri)
    }
    val csvCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CsvArchiveFormatter.MIME_TYPE),
    ) { uri ->
        coordinator.onCreateDocumentResult(uri)
    }
    val pdfCreateDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PdfArchiveFormatter.MIME_TYPE),
    ) { uri ->
        coordinator.onCreateDocumentResult(uri)
    }

    LaunchedEffect(coordinator, snackbarHostState) {
        coordinator.events.collect { event ->
            when (event) {
                is ArchiveExportUiEvent.RequestCreateDocument -> {
                    when (event.mimeType) {
                        MarkdownArchiveFormatter.MIME_TYPE -> {
                            markdownCreateDocumentLauncher.launch(event.suggestedFileName)
                        }
                        CsvArchiveFormatter.MIME_TYPE -> {
                            csvCreateDocumentLauncher.launch(event.suggestedFileName)
                        }
                        PdfArchiveFormatter.MIME_TYPE -> {
                            pdfCreateDocumentLauncher.launch(event.suggestedFileName)
                        }
                    }
                }
                ArchiveExportUiEvent.NoAnswers -> {
                    snackbarHostState.showPracticeInfoSnackbar(noAnswersMessage)
                }
                ArchiveExportUiEvent.Success -> {
                    snackbarHostState.showPracticeInfoSnackbar(successMessage)
                }
                ArchiveExportUiEvent.WriteFailed -> {
                    snackbarHostState.showPracticeInfoSnackbar(writeErrorMessage)
                }
            }
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
