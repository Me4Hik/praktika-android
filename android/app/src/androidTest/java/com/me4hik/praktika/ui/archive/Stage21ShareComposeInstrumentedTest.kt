// 07.08.2026 Stage 21 Share cursor by Me4Hik START - isolated Compose share wiring tests
package com.me4hik.praktika.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.export.ArchiveExportUseCase
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.export.ExportSelection
import com.me4hik.praktika.export.share.EntryShareMode
import com.me4hik.praktika.share.ShareTempFileStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage21ShareComposeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dayEntryShareQuestionOnlyEmitsPayload() {
        val events = mutableListOf<ArchiveShareUiEvent>()
        composeRule.setContent {
            var pendingEntryShare by remember { mutableStateOf<EntryShareRequest?>(null) }
            val scope = rememberCoroutineScope()
            val shareRoot = remember { File.createTempFile("share-compose", null).apply { delete(); mkdir() } }
            val coordinator = remember {
                ArchiveShareCoordinator(
                    exportUseCase = ArchiveExportUseCase(
                        archiveReadRepository = AndroidTestArchiveReadRepository(),
                        zoneIdProvider = FixedAndroidTestZoneIdProvider(),
                    ),
                    tempFileStore = ShareTempFileStore(shareRoot),
                    scope = scope,
                )
            }
            LaunchedEffect(coordinator) {
                coordinator.events.collect { events += it }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ArchiveDayScreen(
                    uiState = ArchiveDayUiState.Content(
                        titleDateText = "7 августа 2026",
                        entries = listOf(
                            ArchiveDayEntryUi(
                                answerId = 42L,
                                questionText = "Snapshot question",
                                answerText = "Snapshot answer",
                                dateText = "7 августа 2026",
                                timeText = "12:00",
                            ),
                        ),
                    ),
                    deleteUiState = ArchiveDeleteUiState(),
                    onExportDay = {},
                    onShareDay = {},
                    onBack = {},
                    onRequestDelete = {},
                    onRequestShare = { questionText, answerText ->
                        pendingEntryShare = EntryShareRequest(questionText, answerText)
                    },
                    onCancelDelete = {},
                    onConfirmDelete = {},
                )
            }
            pendingEntryShare?.let { request ->
                ArchiveEntryShareDialog(
                    onDismiss = { pendingEntryShare = null },
                    onQuestionOnly = {
                        pendingEntryShare = null
                        coordinator.shareEntry(
                            mode = EntryShareMode.QUESTION_ONLY,
                            questionText = request.questionText,
                            answerText = request.answerText,
                        )
                    },
                    onQuestionAndAnswer = {
                        pendingEntryShare = null
                        coordinator.shareEntry(
                            mode = EntryShareMode.QUESTION_AND_ANSWER,
                            questionText = request.questionText,
                            answerText = request.answerText,
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithTag("${ArchiveTestTags.SHARE_BUTTON_PREFIX}42").performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_QUESTION_ONLY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            events.any { it is ArchiveShareUiEvent.ShareText }
        }
        val event = events.filterIsInstance<ArchiveShareUiEvent.ShareText>().single()
        assertEquals("Snapshot question", event.payload)
    }

    @Test
    fun dayEntryShareQuestionAndAnswerEmitsPayload() {
        val events = mutableListOf<ArchiveShareUiEvent>()
        composeRule.setContent {
            var pendingEntryShare by remember { mutableStateOf<EntryShareRequest?>(null) }
            val scope = rememberCoroutineScope()
            val shareRoot = remember { File.createTempFile("share-compose", null).apply { delete(); mkdir() } }
            val coordinator = remember {
                ArchiveShareCoordinator(
                    exportUseCase = ArchiveExportUseCase(
                        archiveReadRepository = AndroidTestArchiveReadRepository(),
                        zoneIdProvider = FixedAndroidTestZoneIdProvider(),
                    ),
                    tempFileStore = ShareTempFileStore(shareRoot),
                    scope = scope,
                )
            }
            LaunchedEffect(coordinator) {
                coordinator.events.collect { events += it }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ArchiveQuestionHistoryScreen(
                    uiState = ArchiveQuestionHistoryUiState.Content(
                        entries = listOf(
                            ArchiveQuestionHistoryItem(
                                stableKey = "a:7",
                                kind = ArchiveHistoryItemKind.Answer,
                                occurrenceId = 7L,
                                questionText = "History snapshot",
                                answerId = 7L,
                                answerText = "History answer",
                                dateTimeText = "7 августа 2026 · 12:00",
                                cycleNumber = 2,
                                cyclePosition = 1,
                                durationMinutes = null,
                                canShare = true,
                                canDelete = true,
                            ),
                        ),
                    ),
                    deleteUiState = ArchiveDeleteUiState(),
                    onExportHistory = {},
                    onShareHistory = {},
                    onBack = {},
                    onRequestDelete = {},
                    onRequestShare = { questionText, answerText ->
                        pendingEntryShare = EntryShareRequest(questionText, answerText)
                    },
                    onCancelDelete = {},
                    onConfirmDelete = {},
                )
            }
            pendingEntryShare?.let { request ->
                ArchiveEntryShareDialog(
                    onDismiss = { pendingEntryShare = null },
                    onQuestionOnly = {
                        pendingEntryShare = null
                        coordinator.shareEntry(
                            mode = EntryShareMode.QUESTION_ONLY,
                            questionText = request.questionText,
                            answerText = request.answerText,
                        )
                    },
                    onQuestionAndAnswer = {
                        pendingEntryShare = null
                        coordinator.shareEntry(
                            mode = EntryShareMode.QUESTION_AND_ANSWER,
                            questionText = request.questionText,
                            answerText = request.answerText,
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithTag("${ArchiveTestTags.SHARE_BUTTON_PREFIX}7").performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.ENTRY_SHARE_QUESTION_AND_ANSWER).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            events.any { it is ArchiveShareUiEvent.ShareText }
        }
        val event = events.filterIsInstance<ArchiveShareUiEvent.ShareText>().single()
        assertEquals("History snapshot\n\nHistory answer", event.payload)
    }

    @Test
    fun archiveShareAllMarkdownEmitsFileEvent() {
        val events = mutableListOf<ArchiveShareUiEvent>()
        val repository = AndroidTestArchiveReadRepository()
        repository.emit(listOf(androidTestArchiveEntry(1, 1_000L)))
        composeRule.setContent {
            var pendingShareSelection by remember { mutableStateOf<ExportSelection?>(null) }
            val scope = rememberCoroutineScope()
            val shareRoot = remember { File.createTempFile("share-compose", null).apply { delete(); mkdir() } }
            val coordinator = remember {
                ArchiveShareCoordinator(
                    exportUseCase = ArchiveExportUseCase(
                        archiveReadRepository = repository,
                        zoneIdProvider = FixedAndroidTestZoneIdProvider(),
                    ),
                    tempFileStore = ShareTempFileStore(shareRoot),
                    scope = scope,
                )
            }
            LaunchedEffect(coordinator) {
                coordinator.events.collect { events += it }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ArchiveHubScreen(
                    onOpenDays = {},
                    onOpenQuestions = {},
                    onOpenInsights = {},
                    onExportAll = {},
                    onExportPeriod = {},
                    onShareAll = { pendingShareSelection = ExportSelection.All },
                    onSharePeriod = {},
                    onBack = {},
                )
            }
            pendingShareSelection?.let { selection ->
                ArchiveExportFormatDialog(
                    onDismiss = { pendingShareSelection = null },
                    onFormatSelected = { format ->
                        pendingShareSelection = null
                        coordinator.requestFileShare(selection, format)
                    },
                    titleRes = com.me4hik.praktika.R.string.archive_share_format_dialog_title,
                    dialogTestTag = ArchiveTestTags.SHARE_FORMAT_DIALOG,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_FORMAT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_MARKDOWN).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            events.any { it is ArchiveShareUiEvent.ShareFile }
        }
        val event = events.filterIsInstance<ArchiveShareUiEvent.ShareFile>().single()
        assertEquals("praktika-all.md", event.suggestedFileName)
        assertTrue(event.file.length() > 0L)
    }

    @Test
    fun archiveShareEmptySelectionEmitsNoAnswers() {
        val events = mutableListOf<ArchiveShareUiEvent>()
        composeRule.setContent {
            var pendingShareSelection by remember { mutableStateOf<ExportSelection?>(null) }
            val scope = rememberCoroutineScope()
            val shareRoot = remember { File.createTempFile("share-compose", null).apply { delete(); mkdir() } }
            val coordinator = remember {
                ArchiveShareCoordinator(
                    exportUseCase = ArchiveExportUseCase(
                        archiveReadRepository = AndroidTestArchiveReadRepository(),
                        zoneIdProvider = FixedAndroidTestZoneIdProvider(),
                    ),
                    tempFileStore = ShareTempFileStore(shareRoot),
                    scope = scope,
                )
            }
            LaunchedEffect(coordinator) {
                coordinator.events.collect { events += it }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ArchiveHubScreen(
                    onOpenDays = {},
                    onOpenQuestions = {},
                    onOpenInsights = {},
                    onExportAll = {},
                    onExportPeriod = {},
                    onShareAll = { pendingShareSelection = ExportSelection.All },
                    onSharePeriod = {},
                    onBack = {},
                )
            }
            pendingShareSelection?.let { selection ->
                ArchiveExportFormatDialog(
                    onDismiss = { pendingShareSelection = null },
                    onFormatSelected = { format ->
                        pendingShareSelection = null
                        coordinator.requestFileShare(selection, format)
                    },
                    titleRes = com.me4hik.praktika.R.string.archive_share_format_dialog_title,
                    dialogTestTag = ArchiveTestTags.SHARE_FORMAT_DIALOG,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CSV).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            events.contains(ArchiveShareUiEvent.NoAnswers)
        }
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
