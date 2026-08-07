// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - isolated coordinator/snackbar tests
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - format-aware coordinator tests
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage19ArchiveExportComposeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulMockedWriteShowsSavedSnackbar() {
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val repository = remember { AndroidTestArchiveReadRepository() }
            repository.emit(listOf(androidTestArchiveEntry(1, 1_000L)))
            val coordinator = remember {
                ArchiveExportCoordinator(
                    exportUseCase = ArchiveExportUseCase(
                        archiveReadRepository = repository,
                        zoneIdProvider = FixedAndroidTestZoneIdProvider(),
                    ),
                    documentWriter = AndroidTestExportDocumentWriter(shouldFail = false),
                    scope = scope,
                )
            }
            LaunchedEffect(coordinator, snackbarHostState) {
                coordinator.events.collect { event ->
                    when (event) {
                        is ArchiveExportUiEvent.RequestCreateDocument -> {
                            coordinator.onCreateDocumentResult(
                                android.net.Uri.parse("content://test/saved.md"),
                            )
                        }
                        ArchiveExportUiEvent.Success -> {
                            snackbarHostState.showSnackbar("Файл сохранён")
                        }
                        ArchiveExportUiEvent.WriteFailed -> Unit
                        ArchiveExportUiEvent.NoAnswers -> Unit
                    }
                }
            }
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(onClick = {
                        coordinator.requestExport(ExportSelection.All, ExportFormat.MARKDOWN)
                    }) {
                        Text("Trigger export")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Trigger export").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("Файл сохранён").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun failedMockedWriteShowsErrorSnackbar() {
        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val repository = remember { AndroidTestArchiveReadRepository() }
            repository.emit(listOf(androidTestArchiveEntry(1, 1_000L)))
            val coordinator = remember {
                ArchiveExportCoordinator(
                    exportUseCase = ArchiveExportUseCase(
                        archiveReadRepository = repository,
                        zoneIdProvider = FixedAndroidTestZoneIdProvider(),
                    ),
                    documentWriter = AndroidTestExportDocumentWriter(shouldFail = true),
                    scope = scope,
                )
            }
            LaunchedEffect(coordinator, snackbarHostState) {
                coordinator.events.collect { event ->
                    when (event) {
                        is ArchiveExportUiEvent.RequestCreateDocument -> {
                            coordinator.onCreateDocumentResult(
                                android.net.Uri.parse("content://test/saved.md"),
                            )
                        }
                        ArchiveExportUiEvent.WriteFailed -> {
                            snackbarHostState.showSnackbar("Не удалось сохранить файл")
                        }
                        ArchiveExportUiEvent.Success -> Unit
                        ArchiveExportUiEvent.NoAnswers -> Unit
                    }
                }
            }
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(onClick = {
                        coordinator.requestExport(ExportSelection.All, ExportFormat.CSV)
                    }) {
                        Text("Trigger export fail")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Trigger export fail").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("Не удалось сохранить файл").assertExists()
                true
            }.getOrDefault(false)
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
