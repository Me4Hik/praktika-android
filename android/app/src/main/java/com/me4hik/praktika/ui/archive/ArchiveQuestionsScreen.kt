// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - экран списка вопросов архива
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
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
import com.me4hik.praktika.ui.theme.HomeQuestionSerifStyle
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun ArchiveQuestionsScreen(
    uiState: ArchiveQuestionsUiState,
    onQuestionSelected: (questionId: Int) -> Unit,
    onBack: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - archive questions glass layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ArchiveTestTags.QUESTIONS_SCREEN),
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
                text = stringResource(R.string.archive_questions_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (uiState) {
                ArchiveQuestionsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TextPrimary)
                    }
                }
                ArchiveQuestionsUiState.Empty -> {
                    Text(
                        text = stringResource(R.string.archive_empty),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .testTag(ArchiveTestTags.QUESTIONS_EMPTY),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                }
                is ArchiveQuestionsUiState.Error -> {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is ArchiveQuestionsUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(ArchiveTestTags.QUESTIONS_LIST),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            PracticeSectionHeader(
                                title = stringResource(R.string.archive_questions_title),
                                icon = Icons.Outlined.HelpOutline,
                            )
                        }
                        itemsIndexed(uiState.questions, key = { _, item -> item.questionId }) { index, questionItem ->
                            val summarySegments = ArchiveQuestionSummaryFormatter.nonzeroSegments(
                                answerCount = questionItem.answerCount,
                                rejectedCount = questionItem.rejectedCount,
                                missedCount = questionItem.missedCount,
                                deferredCount = questionItem.deferredCount,
                            )
                            val supporting = if (summarySegments.isEmpty()) {
                                null
                            } else {
                                ArchiveQuestionSummaryFormatter.join(
                                    summarySegments.map { segment ->
                                        when (segment.kind) {
                                            ArchiveSummaryCountKind.Answer -> stringResource(
                                                R.string.archive_question_answer_count,
                                                segment.count,
                                            )
                                            ArchiveSummaryCountKind.Rejected -> stringResource(
                                                R.string.archive_question_rejected_count,
                                                segment.count,
                                            )
                                            ArchiveSummaryCountKind.Missed -> stringResource(
                                                R.string.archive_question_missed_count,
                                                segment.count,
                                            )
                                            ArchiveSummaryCountKind.Deferred -> stringResource(
                                                R.string.archive_question_deferred_count,
                                                segment.count,
                                            )
                                        }
                                    },
                                )
                            }
                            ArchiveListRow(
                                title = questionItem.questionText,
                                titleStyle = HomeQuestionSerifStyle,
                                supporting = supporting,
                                leading = {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextQuestionSoft.copy(alpha = 0.55f),
                                    )
                                },
                                onClick = { onQuestionSelected(questionItem.questionId) },
                                modifier = Modifier.testTag(
                                    "${ArchiveTestTags.QUESTION_ITEM_PREFIX}${questionItem.questionId}",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}
// 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END
