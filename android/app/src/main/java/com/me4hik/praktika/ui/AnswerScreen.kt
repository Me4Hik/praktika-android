// 04.08.2026 Reminder App cursor by Me4Hik START - экран ответа
// 05.08.2026 Question And Skip cursor by Me4Hik START - честная заглушка без ввода и сохранения
// 05.08.2026 Answer Save cursor by Me4Hik START - полноценный экран ввода и сохранения
package com.me4hik.praktika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticePrimaryButton
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.PracticeWritingSurface
import com.me4hik.praktika.ui.practice.AnswerBlockedReason
import com.me4hik.praktika.ui.practice.AnswerFatalError
import com.me4hik.praktika.ui.practice.AnswerSaveError
import com.me4hik.praktika.ui.practice.AnswerUiState
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.HomeQuestionSerifStyle
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TextSecondary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun AnswerScreen(
    uiState: AnswerUiState,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onBackHome: () -> Unit,
    onRetry: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - Answer editorial glass layout
    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Answer)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            when (uiState) {
                AnswerUiState.Loading -> AnswerLoadingContent()
                is AnswerUiState.Interactive -> AnswerInteractiveContent(
                    uiState = uiState,
                    onDraftChanged = onDraftChanged,
                    onSave = onSave,
                    onBack = onBack,
                )
                is AnswerUiState.Blocked -> AnswerBlockedContent(
                    uiState = uiState,
                    onBackHome = onBackHome,
                )
                is AnswerUiState.FatalError -> AnswerFatalErrorContent(
                    error = uiState.error,
                    onBackHome = onBackHome,
                    onRetry = onRetry,
                )
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}

@Composable
private fun AnswerTopBar(
    onBack: () -> Unit,
    enabled: Boolean,
    showHero: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .testTag(PracticeTestTags.ANSWER_BACK),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.practice_back),
                tint = TextSecondary,
            )
        }
        if (showHero) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(72.dp),
            ) {
                PracticeHeroAccent(
                    modifier = Modifier.fillMaxSize(),
                    widthFraction = 1f,
                    minHeight = 64.dp,
                    maxHeight = 72.dp,
                    glowAlphaMultiplier = 0.55f,
                    contentAlignment = Alignment.TopEnd,
                    showWarmCore = true,
                )
            }
        }
    }
}

@Composable
private fun AnswerLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PracticeTestTags.ANSWER_LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = TextPrimary)
    }
}

@Composable
private fun AnswerInteractiveContent(
    uiState: AnswerUiState.Interactive,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnswerTopBar(
            onBack = onBack,
            enabled = !uiState.isSaving,
            showHero = true,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.answer_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = uiState.questionText,
                style = HomeQuestionSerifStyle,
                color = TextQuestionSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PracticeTestTags.ANSWER_QUESTION_TEXT),
            )
            PracticeWritingSurface(
                value = uiState.draftText,
                onValueChange = onDraftChanged,
                placeholder = stringResource(R.string.answer_input_placeholder),
                enabled = !uiState.isSaving,
                testTag = PracticeTestTags.ANSWER_INPUT,
            )
            uiState.saveError?.let { error ->
                Text(
                    text = saveErrorMessage(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(PracticeTestTags.ANSWER_SAVE_ERROR),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            PracticePrimaryButton(
                text = stringResource(R.string.answer_save),
                onClick = onSave,
                enabled = uiState.canSave && !uiState.isSaving,
                showProgress = uiState.isSaving,
                progressTestTag = PracticeTestTags.ANSWER_SAVE_PROGRESS,
                modifier = Modifier.testTag(PracticeTestTags.ANSWER_SAVE),
            )
        }
    }
}

@Composable
private fun AnswerBlockedContent(
    uiState: AnswerUiState.Blocked,
    onBackHome: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnswerTopBar(
            onBack = onBackHome,
            enabled = true,
            showHero = false,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag(PracticeTestTags.ANSWER_BLOCKED),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.answer_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
            )
            if (!uiState.questionText.isNullOrBlank()) {
                Text(
                    text = uiState.questionText,
                    style = HomeQuestionSerifStyle,
                    color = TextQuestionSoft,
                    modifier = Modifier.testTag(PracticeTestTags.ANSWER_QUESTION_TEXT),
                )
            }
            PracticeSurface {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = blockedTitle(uiState.reason),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = blockedMessage(uiState.reason),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }
            PracticePrimaryButton(
                text = stringResource(R.string.question_back_home),
                onClick = onBackHome,
                enabled = true,
            )
        }
    }
}

@Composable
private fun AnswerFatalErrorContent(
    error: AnswerFatalError,
    onBackHome: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AnswerTopBar(
            onBack = onBackHome,
            enabled = true,
            showHero = false,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag(PracticeTestTags.ANSWER_FATAL_ERROR),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.answer_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
            )
            PracticeSurface {
                Text(
                    text = when (error) {
                        AnswerFatalError.CORRUPTION -> stringResource(R.string.practice_error_corruption)
                        AnswerFatalError.LOAD_FAILED -> stringResource(R.string.practice_error_load_failed)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.practice_retry),
                    color = AccentViolet.copy(alpha = 0.90f),
                    textAlign = TextAlign.Center,
                )
            }
            PracticePrimaryButton(
                text = stringResource(R.string.question_back_home),
                onClick = onBackHome,
                enabled = true,
            )
        }
    }
}

@Composable
private fun saveErrorMessage(error: AnswerSaveError): String {
    return when (error) {
        AnswerSaveError.BLANK -> stringResource(R.string.answer_error_blank)
        AnswerSaveError.OCCURRENCE_CHANGED -> stringResource(R.string.answer_error_occurrence_changed)
        AnswerSaveError.SAVE_FAILED -> stringResource(R.string.answer_error_save_failed)
    }
}

@Composable
private fun blockedTitle(reason: AnswerBlockedReason): String {
    return when (reason) {
        AnswerBlockedReason.NOT_FOUND -> stringResource(R.string.answer_blocked_not_found_title)
        AnswerBlockedReason.NOT_AVAILABLE_YET -> stringResource(R.string.question_blocked_not_available_title)
        AnswerBlockedReason.PRACTICE_PAUSED -> stringResource(R.string.practice_paused_title)
        AnswerBlockedReason.ALREADY_COMPLETED -> stringResource(R.string.question_blocked_completed_title)
        AnswerBlockedReason.NOT_CURRENT -> stringResource(R.string.question_blocked_not_current_title)
    }
}

@Composable
private fun blockedMessage(reason: AnswerBlockedReason): String {
    return when (reason) {
        AnswerBlockedReason.NOT_FOUND -> stringResource(R.string.answer_blocked_not_found_message)
        AnswerBlockedReason.NOT_AVAILABLE_YET -> stringResource(R.string.question_blocked_not_available_message)
        AnswerBlockedReason.PRACTICE_PAUSED -> stringResource(R.string.question_blocked_paused_message)
        AnswerBlockedReason.ALREADY_COMPLETED -> stringResource(R.string.question_blocked_completed_message)
        AnswerBlockedReason.NOT_CURRENT -> stringResource(R.string.question_blocked_not_current_message)
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
// 05.08.2026 Question And Skip cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
