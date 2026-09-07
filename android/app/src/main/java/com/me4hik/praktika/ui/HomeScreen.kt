// 04.08.2026 Reminder App cursor by Me4Hik START - главный экран
// 05.08.2026 Main Screen cursor by Me4Hik START - состояния SCHEDULED/AVAILABLE/PAUSED
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission card
package com.me4hik.praktika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
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
import com.me4hik.praktika.ui.components.MetadataText
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticePrimaryButton
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.practice.HomeExactAlarmCardState
import com.me4hik.praktika.ui.practice.HomeNotificationCardState
import com.me4hik.praktika.ui.practice.MainContentUiState
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.tour.TourTargetId
import com.me4hik.praktika.ui.tour.tourTarget
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.HomeQuestionSerifStyle
import com.me4hik.praktika.ui.theme.HomeTitleSerifStyle
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    content: MainContentUiState,
    notificationCard: HomeNotificationCardState?,
    exactAlarmCard: HomeExactAlarmCardState? = null,
    onNotificationCardAction: () -> Unit,
    onExactAlarmCardAction: () -> Unit = {},
    onOpenQuestion: (occurrenceId: Long) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - Home glass layout
    val isPaused = content is MainContentUiState.PausedScheduled ||
        content is MainContentUiState.PausedAvailable
    val heroWidthFraction = if (isPaused) 0.36f else 0.42f

    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Home)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            notificationCard?.let { card ->
                HomeNotificationCard(
                    card = card,
                    onAction = onNotificationCardAction,
                )
            }
            exactAlarmCard?.let {
                HomeExactAlarmCard(onAction = onExactAlarmCardAction)
            }

            PracticeHeroAccent(widthFraction = heroWidthFraction)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tourTarget(TourTargetId.HOME_STATUS),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeStatusTitle(content = content)

                MetadataText(
                    text = stringResource(
                        R.string.practice_question_position,
                        content.occurrence.cyclePosition,
                    ),
                    modifier = Modifier.testTag(PracticeTestTags.HOME_POSITION),
                )

                when (content) {
                    is MainContentUiState.Scheduled -> {
                        HomeScheduledContent(content = content)
                    }
                    is MainContentUiState.Available -> {
                        HomeAvailableContent(
                            content = content,
                            onOpenQuestion = onOpenQuestion,
                        )
                    }
                    is MainContentUiState.PausedScheduled -> {
                        HomePausedScheduledContent(content = content)
                    }
                    is MainContentUiState.PausedAvailable -> {
                        HomePausedAvailableContent(content = content)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PracticeSurface(contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.practice_archive),
                        icon = Icons.Outlined.Archive,
                        onClick = onOpenArchive,
                        modifier = Modifier
                            .testTag(PracticeTestTags.HOME_ARCHIVE)
                            .tourTarget(TourTargetId.HOME_ARCHIVE),
                    )
                }
                PracticeSurface(contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    PracticeGlassActionRow(
                        title = stringResource(R.string.practice_settings),
                        icon = Icons.Outlined.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .testTag(PracticeTestTags.HOME_SETTINGS)
                            .tourTarget(TourTargetId.HOME_SETTINGS),
                    )
                }
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}

@Composable
private fun HomeStatusTitle(content: MainContentUiState) {
    val title = when (content) {
        is MainContentUiState.Scheduled,
        is MainContentUiState.Available,
        -> stringResource(R.string.practice_started_title)
        is MainContentUiState.PausedScheduled,
        is MainContentUiState.PausedAvailable,
        -> stringResource(R.string.practice_paused_title)
    }
    val modifier = if (
        content is MainContentUiState.PausedScheduled ||
        content is MainContentUiState.PausedAvailable
    ) {
        Modifier.testTag(PracticeTestTags.HOME_PAUSED)
    } else {
        Modifier
    }
    Text(
        text = title,
        style = HomeTitleSerifStyle,
        color = TextQuestionSoft,
        textAlign = TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeScheduledContent(content: MainContentUiState.Scheduled) {
    MetadataText(text = stringResource(R.string.practice_next_question))
    HomeTimingRow(
        label = stringResource(R.string.practice_planned_time),
        value = content.occurrence.plannedAtText,
        valueTestTag = PracticeTestTags.HOME_PLANNED_TIME,
    )
}

@Composable
private fun HomeAvailableContent(
    content: MainContentUiState.Available,
    onOpenQuestion: (occurrenceId: Long) -> Unit,
) {
    Text(
        text = content.occurrence.questionText,
        style = HomeQuestionSerifStyle,
        color = TextQuestionSoft,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(PracticeTestTags.HOME_QUESTION_TEXT),
    )
    HomeTimingRow(
        label = stringResource(R.string.practice_available_until),
        value = content.occurrence.availableUntilText,
        valueTestTag = PracticeTestTags.HOME_AVAILABLE_UNTIL,
    )
    Spacer(modifier = Modifier.height(8.dp))
    PracticePrimaryButton(
        text = stringResource(R.string.practice_answer),
        onClick = { onOpenQuestion(content.occurrence.occurrenceId) },
        enabled = true,
        modifier = Modifier.testTag(PracticeTestTags.HOME_ANSWER),
    )
}

@Composable
private fun HomePausedScheduledContent(content: MainContentUiState.PausedScheduled) {
    MetadataText(text = stringResource(R.string.practice_next_question))
    HomeTimingRow(
        label = stringResource(R.string.practice_planned_time),
        value = content.occurrence.plannedAtText,
        valueTestTag = PracticeTestTags.HOME_PLANNED_TIME,
    )
}

@Composable
private fun HomePausedAvailableContent(content: MainContentUiState.PausedAvailable) {
    Text(
        text = content.occurrence.questionText,
        style = HomeQuestionSerifStyle,
        color = TextQuestionSoft,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(PracticeTestTags.HOME_QUESTION_TEXT),
    )
    HomeTimingRow(
        label = stringResource(R.string.practice_saved_deadline),
        value = content.occurrence.availableUntilText,
        valueTestTag = PracticeTestTags.HOME_AVAILABLE_UNTIL,
    )
}

@Composable
private fun HomeTimingRow(
    label: String,
    value: String,
    valueTestTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Event,
            contentDescription = null,
            tint = AccentViolet.copy(alpha = 0.65f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetadataText(text = label)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.testTag(valueTestTag),
            )
        }
    }
}

@Composable
private fun HomeNotificationCard(
    card: HomeNotificationCardState,
    onAction: () -> Unit,
) {
    val message = when (card) {
        HomeNotificationCardState.REQUEST_RUNTIME_PERMISSION ->
            stringResource(R.string.home_notification_request_message)
        HomeNotificationCardState.OPEN_APP_NOTIFICATION_SETTINGS,
        HomeNotificationCardState.OPEN_CHANNEL_SETTINGS,
        -> stringResource(R.string.home_notification_disabled_message)
    }
    val actionLabel = when (card) {
        HomeNotificationCardState.REQUEST_RUNTIME_PERMISSION ->
            stringResource(R.string.home_notification_request_action)
        HomeNotificationCardState.OPEN_APP_NOTIFICATION_SETTINGS,
        HomeNotificationCardState.OPEN_CHANNEL_SETTINGS,
        -> stringResource(R.string.home_notification_enable_action)
    }
    PracticeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PracticeTestTags.HOME_NOTIFICATION_CARD),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = AccentViolet.copy(alpha = 0.70f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.testTag(PracticeTestTags.HOME_NOTIFICATION_STATUS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.testTag(PracticeTestTags.HOME_NOTIFICATION_ACTION),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentViolet.copy(alpha = 0.90f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeExactAlarmCard(
    onAction: () -> Unit,
) {
    PracticeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PracticeTestTags.HOME_EXACT_ALARM_CARD),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Event,
                contentDescription = null,
                tint = AccentViolet.copy(alpha = 0.70f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_exact_alarm_message),
                    modifier = Modifier.testTag(PracticeTestTags.HOME_EXACT_ALARM_STATUS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.testTag(PracticeTestTags.HOME_EXACT_ALARM_ACTION),
                ) {
                    Text(
                        text = stringResource(R.string.home_exact_alarm_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentViolet.copy(alpha = 0.90f),
                    )
                }
            }
        }
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
