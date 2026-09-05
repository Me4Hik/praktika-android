// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - экран списка дат архива
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
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
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.PracticeTopBar
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TimeSerifStyle
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun ArchiveDatesScreen(
    uiState: ArchiveDatesUiState,
    onDateSelected: (epochDay: Long) -> Unit,
    onOpenQuestions: () -> Unit,
    onOpenInsights: () -> Unit,
    onExportAll: () -> Unit,
    onExportPeriod: () -> Unit,
    onShareAll: () -> Unit,
    onSharePeriod: () -> Unit,
    onBack: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - archive dates glass layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ArchiveTestTags.DATES_SCREEN),
    ) {
        PracticeBackground(style = PracticeBackgroundStyle.Archive)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            PracticeTopBar(
                onBack = onBack,
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
                text = stringResource(R.string.archive_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (uiState) {
                ArchiveDatesUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                ArchiveDatesUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.archive_empty),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .testTag(ArchiveTestTags.DATES_EMPTY),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is ArchiveDatesUiState.Error -> {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                is ArchiveDatesUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(ArchiveTestTags.DATES_LIST),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            PracticeSectionHeader(
                                title = stringResource(R.string.archive_title),
                                icon = Icons.Outlined.CalendarMonth,
                            )
                        }
                        items(uiState.dates, key = { it.epochDay }) { dateItem ->
                            val supporting = if (dateItem.answerCount > 1) {
                                stringResource(
                                    R.string.archive_question_answer_count,
                                    dateItem.answerCount,
                                )
                            } else {
                                null
                            }
                            ArchiveListRow(
                                title = dateItem.dateText,
                                supporting = supporting,
                                titleStyle = TimeSerifStyle,
                                onClick = { onDateSelected(dateItem.epochDay) },
                                modifier = Modifier.testTag(
                                    "${ArchiveTestTags.DATE_ITEM_PREFIX}${dateItem.epochDay}",
                                ),
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (uiState is ArchiveDatesUiState.Content && uiState.dates.isNotEmpty()) {
                    ArchiveDecorativeSeparator()
                }
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_open_questions),
                        icon = Icons.Outlined.HelpOutline,
                        onClick = onOpenQuestions,
                        modifier = Modifier.testTag(ArchiveTestTags.OPEN_QUESTIONS),
                    )
                }
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.archive_open_insights),
                        icon = Icons.Outlined.Insights,
                        onClick = onOpenInsights,
                        modifier = Modifier.testTag(ArchiveTestTags.OPEN_INSIGHTS),
                    )
                }
                PracticeSurface(contentPadding = PaddingValues(4.dp)) {
                    Column {
                        PracticeGlassActionRow(
                            title = stringResource(R.string.archive_export_all),
                            icon = Icons.Outlined.FileDownload,
                            showChevron = false,
                            onClick = onExportAll,
                            modifier = Modifier.testTag(ArchiveTestTags.EXPORT_ALL),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.archive_export_period),
                            icon = Icons.Outlined.FileDownload,
                            showChevron = false,
                            onClick = onExportPeriod,
                            modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.archive_share_all),
                            icon = Icons.Outlined.Share,
                            showChevron = false,
                            onClick = onShareAll,
                            modifier = Modifier.testTag(ArchiveTestTags.SHARE_ALL),
                        )
                        PracticeGlassActionRow(
                            title = stringResource(R.string.archive_share_period),
                            icon = Icons.Outlined.Share,
                            showChevron = false,
                            onClick = onSharePeriod,
                            modifier = Modifier.testTag(ArchiveTestTags.SHARE_PERIOD),
                        )
                    }
                }
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
