// 04.08.2026 Reminder App cursor by Me4Hik START - экран вопроса
// 05.08.2026 Main Screen cursor by Me4Hik START - техническая заглушка без фиктивного вопроса
// 05.08.2026 Question And Skip cursor by Me4Hik START - snapshot и skip конкретного occurrence
package com.me4hik.praktika.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.MetadataText
import com.me4hik.praktika.ui.components.QuestionDisplayText
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.practice.QuestionBlockedReason
import com.me4hik.praktika.ui.practice.QuestionCommandState
import com.me4hik.praktika.ui.practice.QuestionFatalError
import com.me4hik.praktika.ui.practice.QuestionUiModel
import com.me4hik.praktika.ui.practice.QuestionUiState
import com.me4hik.praktika.ui.question.QuestionAmbientBackground
import com.me4hik.praktika.ui.question.QuestionHeroOrb
import com.me4hik.praktika.ui.question.QuestionLayoutTokens
import com.me4hik.praktika.ui.question.QuestionPrimaryButton
import com.me4hik.praktika.ui.question.rememberQuestionLayoutTokens
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary

@Composable
fun QuestionScreen(
    uiState: QuestionUiState,
    commandState: QuestionCommandState,
    onAnswer: () -> Unit,
    onDefer: () -> Unit,
    onSkip: () -> Unit,
    onBackHome: () -> Unit,
    onRetry: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - dark ambient shell for all question states
    Box(modifier = Modifier.fillMaxSize()) {
        QuestionAmbientBackground()
        when (uiState) {
            QuestionUiState.Loading -> QuestionLoadingContent()
            is QuestionUiState.Interactive -> QuestionInteractiveContent(
                question = uiState.question,
                commandState = commandState,
                onAnswer = onAnswer,
                onDefer = onDefer,
                onSkip = onSkip,
            )
            is QuestionUiState.Blocked -> QuestionBlockedContent(
                question = uiState.question,
                reason = uiState.reason,
                onBackHome = onBackHome,
            )
            is QuestionUiState.FatalError -> QuestionFatalContent(
                error = uiState.error,
                onRetry = onRetry,
                onBackHome = onBackHome,
            )
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}

@Composable
private fun QuestionLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PracticeTestTags.QUESTION_LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = TextPrimary)
    }
}

@Composable
private fun QuestionInteractiveContent(
    question: QuestionUiModel,
    commandState: QuestionCommandState,
    onAnswer: () -> Unit,
    onDefer: () -> Unit,
    onSkip: () -> Unit,
) {
    val tokens = rememberQuestionLayoutTokens()
    val actionsEnabled = !commandState.isBusy
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (tokens.useCenteredCluster) {
            QuestionCenteredCluster(
                question = question,
                tokens = tokens,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )
        } else {
            QuestionPhoneCluster(
                question = question,
                tokens = tokens,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = tokens.horizontalPadding),
            )
        }

        QuestionActionsColumn(
            commandState = commandState,
            actionsEnabled = actionsEnabled,
            tokens = tokens,
            onAnswer = onAnswer,
            onDefer = onDefer,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun QuestionPhoneCluster(
    question: QuestionUiModel,
    tokens: QuestionLayoutTokens,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(PracticeTestTags.QUESTION_PHONE_CLUSTER)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(tokens.clusterTopPadding))
            MetadataText(
                text = stringResource(
                    R.string.practice_question_position,
                    question.cyclePosition,
                ),
            )
            Spacer(modifier = Modifier.height(tokens.metaToQuestionGap))
            QuestionDisplayText(
                text = question.questionTextSnapshot,
                style = tokens.questionTextStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PracticeTestTags.QUESTION_TEXT),
            )
        }
        Spacer(modifier = Modifier.height(tokens.questionToDropGap))
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .defaultMinSize(minHeight = tokens.orbMinHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            QuestionHeroOrb(
                modifier = Modifier.testTag(PracticeTestTags.QUESTION_HERO_ORB),
                minHeight = tokens.orbMinHeight,
                maxHeight = tokens.orbMaxHeight,
                widthFraction = tokens.orbWidthFraction,
                maxWidth = tokens.orbMaxWidth,
            )
        }
    }
}

@Composable
private fun QuestionCenteredCluster(
    question: QuestionUiModel,
    tokens: QuestionLayoutTokens,
    modifier: Modifier = Modifier,
) {
    // Tablet-02: content is wrap-height; only spacers share leftover space.
    // Competing weight(1f) on content previously capped it at ~1/3 and clipped the orb.
    Column(
        modifier = modifier.testTag(PracticeTestTags.QUESTION_CENTERED_CLUSTER),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .then(contentMaxWidthModifier(tokens.contentMaxWidth))
                .fillMaxWidth()
                .padding(horizontal = tokens.horizontalPadding)
                .verticalScroll(rememberScrollState())
                .testTag(PracticeTestTags.QUESTION_CENTERED_CONTENT),
        ) {
            Spacer(modifier = Modifier.height(tokens.clusterTopPadding))
            MetadataText(
                text = stringResource(
                    R.string.practice_question_position,
                    question.cyclePosition,
                ),
            )
            Spacer(modifier = Modifier.height(tokens.metaToQuestionGap))
            QuestionDisplayText(
                text = question.questionTextSnapshot,
                style = tokens.questionTextStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PracticeTestTags.QUESTION_TEXT),
            )
            Spacer(modifier = Modifier.height(tokens.questionToDropGap))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                QuestionHeroOrb(
                    modifier = Modifier.testTag(PracticeTestTags.QUESTION_HERO_ORB),
                    minHeight = tokens.orbMinHeight,
                    maxHeight = tokens.orbMaxHeight,
                    widthFraction = tokens.orbWidthFraction,
                    maxWidth = tokens.orbMaxWidth,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuestionActionsColumn(
    commandState: QuestionCommandState,
    actionsEnabled: Boolean,
    tokens: QuestionLayoutTokens,
    onAnswer: () -> Unit,
    onDefer: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .then(contentMaxWidthModifier(tokens.contentMaxWidth))
                .fillMaxWidth()
                .padding(horizontal = tokens.horizontalPadding)
                .padding(bottom = 24.dp, top = 8.dp),
        ) {
            QuestionPrimaryButton(
                text = stringResource(R.string.practice_answer),
                onClick = onAnswer,
                enabled = actionsEnabled,
                modifier = Modifier.testTag(PracticeTestTags.QUESTION_ANSWER),
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onDefer,
                enabled = actionsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PracticeTestTags.QUESTION_DEFER),
            ) {
                if (commandState.isDeferring) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag(PracticeTestTags.QUESTION_DEFER_PROGRESS),
                        color = TextSecondary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.question_defer),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onSkip,
                enabled = actionsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PracticeTestTags.QUESTION_SKIP),
            ) {
                if (commandState.isSkipping) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag(PracticeTestTags.QUESTION_SKIP_PROGRESS),
                        color = TextMuted,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.question_skip),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun contentMaxWidthModifier(maxWidth: Dp): Modifier {
    return if (maxWidth == Dp.Infinity) {
        Modifier
    } else {
        Modifier.widthIn(max = maxWidth)
    }
}

@Composable
private fun QuestionBlockedContent(
    question: QuestionUiModel?,
    reason: QuestionBlockedReason,
    onBackHome: () -> Unit,
) {
    val showSnapshot = reason == QuestionBlockedReason.PRACTICE_PAUSED &&
        question?.questionTextSnapshot?.isNotBlank() == true
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(24.dp)
            .testTag(PracticeTestTags.QUESTION_BLOCKED),
    ) {
        Text(
            text = blockedTitle(reason),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = blockedMessage(reason),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        if (showSnapshot) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = question.questionTextSnapshot,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onBackHome,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PracticeTestTags.QUESTION_BACK_HOME),
        ) {
            Text(text = stringResource(R.string.question_back_home))
        }
    }
}

@Composable
private fun QuestionFatalContent(
    error: QuestionFatalError,
    onRetry: () -> Unit,
    onBackHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(24.dp)
            .testTag(PracticeTestTags.QUESTION_FATAL_ERROR),
    ) {
        Text(
            text = when (error) {
                QuestionFatalError.CORRUPTION -> stringResource(R.string.practice_error_corruption)
                QuestionFatalError.LOAD_FAILED -> stringResource(R.string.practice_error_load_failed)
            },
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.practice_retry))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onBackHome,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PracticeTestTags.QUESTION_BACK_HOME),
        ) {
            Text(text = stringResource(R.string.question_back_home))
        }
    }
}

@Composable
private fun blockedTitle(reason: QuestionBlockedReason): String {
    return when (reason) {
        QuestionBlockedReason.NOT_FOUND -> stringResource(R.string.question_blocked_not_found_title)
        QuestionBlockedReason.NOT_AVAILABLE_YET -> stringResource(R.string.question_blocked_not_available_title)
        QuestionBlockedReason.ALREADY_COMPLETED -> stringResource(R.string.question_blocked_completed_title)
        QuestionBlockedReason.NOT_CURRENT -> stringResource(R.string.question_blocked_not_current_title)
        QuestionBlockedReason.PRACTICE_PAUSED -> stringResource(R.string.practice_paused_title)
    }
}

@Composable
private fun blockedMessage(reason: QuestionBlockedReason): String {
    return when (reason) {
        QuestionBlockedReason.NOT_FOUND -> stringResource(R.string.question_blocked_not_found_message)
        QuestionBlockedReason.NOT_AVAILABLE_YET -> stringResource(R.string.question_blocked_not_available_message)
        QuestionBlockedReason.ALREADY_COMPLETED -> stringResource(R.string.question_blocked_completed_message)
        QuestionBlockedReason.NOT_CURRENT -> stringResource(R.string.question_blocked_not_current_message)
        QuestionBlockedReason.PRACTICE_PAUSED -> stringResource(R.string.question_blocked_paused_message)
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
