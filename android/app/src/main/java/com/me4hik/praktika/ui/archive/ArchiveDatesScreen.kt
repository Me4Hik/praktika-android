// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - экран списка дат архива
// PROMPT 167 — date list only on archive/days (hub actions moved to ArchiveHubScreen)
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
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeTopBar
import com.me4hik.praktika.ui.tour.TourTargetId
import com.me4hik.praktika.ui.tour.tourTarget
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TimeSerifStyle
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun ArchiveDatesScreen(
    uiState: ArchiveDatesUiState,
    onDateSelected: (epochDay: Long) -> Unit,
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
                backModifier = Modifier.tourTarget(TourTargetId.ARCHIVE_BACK),
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
                text = stringResource(R.string.archive_by_days_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .tourTarget(TourTargetId.ARCHIVE_DAYS_CONTENT),
            ) {
                when (uiState) {
                    ArchiveDatesUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                    }
                    is ArchiveDatesUiState.Error -> {
                        Text(
                            text = uiState.message,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    is ArchiveDatesUiState.Content -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(ArchiveTestTags.DATES_LIST),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item {
                                PracticeSectionHeader(
                                    title = stringResource(R.string.archive_by_days_title),
                                    icon = Icons.Outlined.CalendarMonth,
                                )
                            }
                            items(uiState.dates, key = { it.epochDay }) { dateItem ->
                                // 06.09.2026 Archive UX polish cursor by Me4Hik START - show Ответов: 1 (hide only for 0)
                                val supporting = if (dateItem.answerCount > 0) {
                                    stringResource(
                                        R.string.archive_question_answer_count,
                                        dateItem.answerCount,
                                    )
                                } else {
                                    null
                                }
                                // 06.09.2026 Archive UX polish cursor by Me4Hik END
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
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
