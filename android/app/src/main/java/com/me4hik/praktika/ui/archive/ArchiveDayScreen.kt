// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - экран ответов за день
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
import com.me4hik.praktika.ui.theme.TimeSerifStyle

@Composable
fun ArchiveDayScreen(
    uiState: ArchiveDayUiState,
    deleteUiState: ArchiveDeleteUiState,
    onExportDay: () -> Unit,
    onShareDay: () -> Unit,
    onBack: () -> Unit,
    onRequestDelete: (Long) -> Unit,
    onRequestShare: (questionText: String, answerText: String) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - archive day journal layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ArchiveTestTags.DAY_SCREEN),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Archive)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            val title = when (uiState) {
                is ArchiveDayUiState.Content -> uiState.titleDateText
                else -> stringResource(R.string.archive_title)
            }
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
                text = title,
                style = TimeSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (uiState) {
                ArchiveDayUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                ArchiveDayUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.archive_day_empty),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is ArchiveDayUiState.Error -> {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is ArchiveDayUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(ArchiveTestTags.DAY_LIST),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.entries, key = { it.answerId }) { entry ->
                            ArchiveEntryCard(
                                questionText = entry.questionText,
                                answerText = entry.answerText,
                                metadata = "${entry.dateText} · ${entry.timeText}",
                                onShare = {
                                    onRequestShare(entry.questionText, entry.answerText)
                                },
                                onDelete = { onRequestDelete(entry.answerId) },
                                shareTestTag = "${ArchiveTestTags.SHARE_BUTTON_PREFIX}${entry.answerId}",
                                deleteTestTag = "${ArchiveTestTags.DELETE_BUTTON_PREFIX}${entry.answerId}",
                                modifier = Modifier.testTag(
                                    "${ArchiveTestTags.DAY_ENTRY_PREFIX}${entry.answerId}",
                                ),
                            )
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
                        title = stringResource(R.string.archive_export_day),
                        icon = Icons.Outlined.FileDownload,
                        showChevron = false,
                        onClick = onExportDay,
                        modifier = Modifier.testTag(ArchiveTestTags.EXPORT_DAY),
                    )
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_share_day),
                        icon = Icons.Outlined.Share,
                        showChevron = false,
                        onClick = onShareDay,
                        modifier = Modifier.testTag(ArchiveTestTags.SHARE_DAY),
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
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
