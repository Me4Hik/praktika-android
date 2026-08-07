// 04.08.2026 Reminder App cursor by Me4Hik START - экран настроек
// 06.08.2026 Settings Schedule cursor by Me4Hik START - полноценный Settings screen
package com.me4hik.praktika.ui

import android.text.format.DateFormat
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.me4hik.praktika.ui.components.PracticePrimaryButton
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.ScheduleSlotRow
import com.me4hik.praktika.ui.settings.SettingsScheduleError
import com.me4hik.praktika.ui.settings.SettingsSlotUiModel
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.settings.SettingsUiState
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.TextMuted
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TextSecondary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSlotTimeChange: (slotIndex: Int, timeOfDayMinutes: Int) -> Unit,
    onSaveSchedule: () -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onTogglePauseState: () -> Unit,
    onBack: () -> Unit,
    onStayOnDirtyBack: () -> Unit,
    onDiscardChanges: () -> Unit,
    showDirtyDialog: Boolean,
) {
    when (uiState) {
        SettingsUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SettingsTestTags.SETTINGS_LOADING)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        is SettingsUiState.FatalError -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SettingsTestTags.SETTINGS_FATAL_ERROR)
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_fatal_error),
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.practice_back),
                    )
                }
            }
        }

        is SettingsUiState.Content -> {
            SettingsContentScreen(
                content = uiState,
                onSlotTimeChange = onSlotTimeChange,
                onSaveSchedule = onSaveSchedule,
                onSoundEnabledChanged = onSoundEnabledChanged,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onTogglePauseState = onTogglePauseState,
                onBack = onBack,
            )
        }
    }

    if (showDirtyDialog) {
        AlertDialog(
            onDismissRequest = onStayOnDirtyBack,
            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_DIRTY_DIALOG),
            title = { Text(text = stringResource(R.string.settings_dirty_back_title)) },
            text = { Text(text = stringResource(R.string.settings_dirty_back_message)) },
            confirmButton = {
                TextButton(
                    onClick = onStayOnDirtyBack,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_STAY),
                ) {
                    Text(text = stringResource(R.string.settings_stay))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDiscardChanges,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_DISCARD),
                ) {
                    Text(text = stringResource(R.string.settings_discard))
                }
            },
        )
    }
}

@Composable
private fun SettingsContentScreen(
    content: SettingsUiState.Content,
    onSlotTimeChange: (slotIndex: Int, timeOfDayMinutes: Int) -> Unit,
    onSaveSchedule: () -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onTogglePauseState: () -> Unit,
    onBack: () -> Unit,
) {
    var pickerSlotIndex by remember { mutableIntStateOf(-1) }

    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - settings glass layout
    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Subdued)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .testTag(SettingsTestTags.SETTINGS_SCREEN),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag(SettingsTestTags.SETTINGS_BACK),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.practice_back),
                        tint = TextSecondary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_schedule_section),
                    icon = Icons.Outlined.Schedule,
                )
                PracticeSurface {
                    content.slots.forEachIndexed { index, slot ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            )
                        }
                        SettingsScheduleSlotRow(
                            slot = slot,
                            enabled = !content.isSavingSchedule,
                            onClick = { pickerSlotIndex = slot.slotIndex },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PracticePrimaryButton(
                        text = stringResource(R.string.settings_save_schedule),
                        onClick = onSaveSchedule,
                        enabled = content.isDirty && content.isScheduleValid && !content.isSavingSchedule,
                        showProgress = content.isSavingSchedule,
                        progressTestTag = SettingsTestTags.SETTINGS_SCHEDULE_PROGRESS,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SCHEDULE_SAVE),
                    )
                }
                content.scheduleError?.let { error ->
                    Text(
                        text = scheduleErrorText(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SCHEDULE_ERROR),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_sound_section),
                    icon = Icons.Outlined.VolumeUp,
                )
                PracticeSurface {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sound_label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                        Switch(
                            checked = content.soundEnabled,
                            onCheckedChange = onSoundEnabledChanged,
                            enabled = !content.isChangingSound,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SOUND_SWITCH),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentViolet.copy(alpha = 0.55f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = TextMuted.copy(alpha = 0.35f),
                            ),
                        )
                    }
                }
                content.soundError?.let {
                    Text(
                        text = stringResource(R.string.settings_sound_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PracticeSurface {
                PracticeGlassActionRow(
                    title = stringResource(R.string.settings_notification_settings),
                    icon = Icons.Outlined.Notifications,
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_practice_section),
                    icon = if (content.isPracticePaused) {
                        Icons.Outlined.PlayCircle
                    } else {
                        Icons.Outlined.PauseCircle
                    },
                )
                PracticeSurface {
                    PracticeGlassActionRow(
                        title = if (content.isPracticePaused) {
                            stringResource(R.string.settings_resume_practice)
                        } else {
                            stringResource(R.string.settings_pause_practice)
                        },
                        icon = if (content.isPracticePaused) {
                            Icons.Outlined.PlayCircle
                        } else {
                            Icons.Outlined.PauseCircle
                        },
                        onClick = onTogglePauseState,
                        enabled = !content.isChangingPauseState,
                        showChevron = false,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_PAUSE_RESUME),
                    )
                    if (content.isChangingPauseState) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag(SettingsTestTags.SETTINGS_PAUSE_PROGRESS),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                content.pauseError?.let {
                    Text(
                        text = stringResource(R.string.settings_pause_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.SETTINGS_ABOUT),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_about_section),
                    icon = Icons.Outlined.Info,
                )
                PracticeSurface {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.width(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.question_hero_liquid_orb),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_about_version,
                                    BuildConfig.VERSION_NAME,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END

    if (pickerSlotIndex > 0) {
        val selectedSlot = content.slots.first { it.slotIndex == pickerSlotIndex }
        SettingsTimePickerDialog(
            initialMinutes = selectedSlot.timeOfDayMinutes,
            onDismiss = { pickerSlotIndex = -1 },
            onConfirm = { minutes ->
                onSlotTimeChange(pickerSlotIndex, minutes)
                pickerSlotIndex = -1
            },
        )
    }
}

@Composable
private fun SettingsScheduleSlotRow(
    slot: SettingsSlotUiModel,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ScheduleSlotRow(
        slotLabel = stringResource(R.string.settings_slot_label, slot.slotIndex),
        timeText = slot.timeText,
        changeLabel = stringResource(R.string.settings_slot_change),
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.testTag(slotTestTag(slot.slotIndex)),
        contentDescription = stringResource(
            R.string.settings_slot_content_description,
            slot.slotIndex,
            slot.timeText,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val context = LocalContext.current
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_TIME_PICKER),
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timePickerState.hour * 60 + timePickerState.minute)
                },
            ) {
                Text(text = stringResource(R.string.settings_time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_time_picker_cancel))
            }
        },
        text = {
            TimePicker(state = timePickerState)
        },
    )
}

@Composable
private fun scheduleErrorText(error: SettingsScheduleError): String {
    return when (error) {
        SettingsScheduleError.DUPLICATE_TIME -> stringResource(R.string.settings_schedule_duplicate_error)
        SettingsScheduleError.SAVE_FAILED -> stringResource(R.string.settings_schedule_save_error)
        SettingsScheduleError.VALIDATION_FAILED -> stringResource(R.string.settings_schedule_validation_error)
    }
}

private fun slotTestTag(slotIndex: Int): String {
    return when (slotIndex) {
        1 -> SettingsTestTags.SETTINGS_SLOT_1
        2 -> SettingsTestTags.SETTINGS_SLOT_2
        3 -> SettingsTestTags.SETTINGS_SLOT_3
        else -> SettingsTestTags.SETTINGS_SLOT_1
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
