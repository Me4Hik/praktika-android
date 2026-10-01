// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - экран истории вопроса
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.PracticeTopBar
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun ArchiveQuestionHistoryScreen(
    uiState: ArchiveQuestionHistoryUiState,
    deleteUiState: ArchiveDeleteUiState,
    onExportHistory: () -> Unit,
    onShareHistory: () -> Unit,
    onBack: () -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestShare: (questionText: String, answerText: String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - archive history journal layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ArchiveTestTags.QUESTION_HISTORY_SCREEN),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Archive)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            PracticeTopBar(
                onBack = onBack,
                enabled = !deleteUiState.isDeleting,
                backTestTag = ArchiveTestTags.BACK,
                trailing = {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(56.dp),
                    ) {
                        PracticeHeroAccent(
                            modifier = Modifier.fillMaxSize(),
                            widthFraction = 1f,
                            minHeight = 48.dp,
                            maxHeight = 56.dp,
                            glowAlphaMultiplier = 0.45f,
                            contentAlignment = Alignment.TopEnd,
                            showWarmCore = false,
                        )
                    }
                },
            )
            Text(
                text = stringResource(R.string.archive_question_history_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (uiState) {
                ArchiveQuestionHistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                ArchiveQuestionHistoryUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.archive_empty),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is ArchiveQuestionHistoryUiState.Error -> {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is ArchiveQuestionHistoryUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(ArchiveTestTags.QUESTION_HISTORY_LIST),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.entries, key = { it.stableKey }) { entry ->
                            val actionTagId = entry.answerId?.toString() ?: entry.stableKey
                            // 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik START - compact defer line
                            val eventLabel = when (entry.kind) {
                                ArchiveHistoryItemKind.Answer -> stringResource(R.string.archive_answer_label)
                                ArchiveHistoryItemKind.Rejected -> stringResource(R.string.archive_rejected_label)
                                ArchiveHistoryItemKind.Missed -> stringResource(R.string.archive_missed_label)
                            }
                            val bodyText = when (entry.kind) {
                                ArchiveHistoryItemKind.Answer -> {
                                    entry.answerText
                                        ?: stringResource(R.string.archive_answer_deleted_body)
                                }
                                ArchiveHistoryItemKind.Rejected,
                                ArchiveHistoryItemKind.Missed,
                                -> null
                            }
                            val deferSummaryText = if (entry.deferCount > 0) {
                                pluralStringResource(
                                    R.plurals.archive_occurrence_deferred_count,
                                    entry.deferCount,
                                    entry.deferCount,
                                )
                            } else {
                                null
                            }
                            ArchiveEntryCard(
                                questionText = null,
                                eventLabel = eventLabel,
                                bodyText = bodyText,
                                deferSummaryText = deferSummaryText,
                                metadata = entry.dateTimeText,
                                cycleLabel = stringResource(
                                    R.string.archive_cycle_label,
                                    entry.cycleNumber,
                                ),
                                onShare = {
                                    val text = entry.answerText
                                    if (entry.canShare && text != null) {
                                        onRequestShare(entry.questionText, text)
                                    }
                                },
                                onDelete = {
                                    val id = entry.answerId
                                    if (entry.canDelete && id != null) {
                                        onRequestDelete(id)
                                    }
                                },
                                showShare = entry.canShare,
                                showDelete = entry.canDelete,
                                shareTestTag = "${ArchiveTestTags.SHARE_BUTTON_PREFIX}$actionTagId",
                                deleteTestTag = "${ArchiveTestTags.DELETE_BUTTON_PREFIX}$actionTagId",
                                modifier = Modifier.testTag(
                                    "${ArchiveTestTags.QUESTION_HISTORY_ENTRY_PREFIX}${entry.stableKey}",
                                ),
                            )
                            // 01.10.2026 Archive T4 occurrence defer line cursor by Me4Hik END
                        }
                    }
                }
            }
            deleteUiState.deleteError?.let {
                Text(
                    text = stringResource(R.string.archive_delete_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .testTag(ArchiveTestTags.DELETE_ERROR),
                )
            }
            PracticeSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                Column {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_export_history),
                        icon = Icons.Outlined.FileDownload,
                        showChevron = false,
                        onClick = onExportHistory,
                        modifier = Modifier.testTag(ArchiveTestTags.EXPORT_HISTORY),
                    )
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_share_history),
                        icon = Icons.Outlined.Share,
                        showChevron = false,
                        onClick = onShareHistory,
                        modifier = Modifier.testTag(ArchiveTestTags.SHARE_HISTORY),
                    )
                }
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END

    if (deleteUiState.isDialogVisible) {
        ArchiveDeleteConfirmationDialog(
            deleteUiState = deleteUiState,
            onCancel = onCancelDelete,
            onConfirm = onConfirmDelete,
        )
    }
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
