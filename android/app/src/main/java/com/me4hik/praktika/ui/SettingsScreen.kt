// 04.08.2026 Reminder App cursor by Me4Hik START - экран настроек
// 06.08.2026 Settings Schedule cursor by Me4Hik START - полноценный Settings screen
// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.R
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeGlassActionRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.me4hik.praktika.ui.components.PracticeSectionHeader
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.ScheduleSlotRow
import com.me4hik.praktika.ui.settings.BugReportDialog
import com.me4hik.praktika.ui.settings.BugReportUiState
import com.me4hik.praktika.ui.settings.SettingsBackupDialogs
import com.me4hik.praktika.ui.settings.SettingsBackupSection
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
    onQuestionWordingModeChanged: (QuestionWordingMode) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onTogglePauseState: () -> Unit,
    onBack: () -> Unit,
    onStayOnDirtyBack: () -> Unit,
    onDiscardChanges: () -> Unit,
    showDirtyDialog: Boolean,
    onOpenBugReport: () -> Unit = {},
    showBugReportDialog: Boolean = false,
    bugReportState: BugReportUiState = BugReportUiState.Idle,
    onDismissBugReport: () -> Unit = {},
    onSubmitBugReport: (String) -> Unit = {},
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
    onBackupSetup: () -> Unit = {},
    onBackupNow: () -> Unit = {},
    onBackupChangeFolder: () -> Unit = {},
    onBackupReconnect: () -> Unit = {},
    onBackupDisable: () -> Unit = {},
    onBackupDisclosureConfirm: () -> Unit = {},
    onBackupDisclosureCancel: () -> Unit = {},
    onBackupCandidateConfirm: () -> Unit = {},
    onBackupCandidateCancel: () -> Unit = {},
    onBackupChooseAnother: () -> Unit = {},
    onBackupCommitRetry: () -> Unit = {},
    onBackupDisableConfirm: () -> Unit = {},
    onBackupDisableCancel: () -> Unit = {},
    onBackupReconnectDifferentUseAsNew: () -> Unit = {},
    onBackupReconnectDifferentCancel: () -> Unit = {},
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
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
                onQuestionWordingModeChanged = onQuestionWordingModeChanged,
                onOpenNotifications = onOpenNotifications,
                onTogglePauseState = onTogglePauseState,
                onOpenBugReport = onOpenBugReport,
                onBack = onBack,
                onBackupSetup = onBackupSetup,
                onBackupNow = onBackupNow,
                onBackupChangeFolder = onBackupChangeFolder,
                onBackupReconnect = onBackupReconnect,
                onBackupDisable = onBackupDisable,
            )
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
            SettingsBackupDialogs(
                backup = uiState.backup,
                onDisclosureConfirm = onBackupDisclosureConfirm,
                onDisclosureCancel = onBackupDisclosureCancel,
                onCandidateConfirm = onBackupCandidateConfirm,
                onCandidateCancel = onBackupCandidateCancel,
                onChooseAnother = onBackupChooseAnother,
                onCommitRetry = onBackupCommitRetry,
                onDisableConfirm = onBackupDisableConfirm,
                onDisableCancel = onBackupDisableCancel,
                onReconnectDifferentUseAsNew = onBackupReconnectDifferentUseAsNew,
                onReconnectDifferentCancel = onBackupReconnectDifferentCancel,
            )
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
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

    if (showBugReportDialog) {
        BugReportDialog(
            bugReportState = bugReportState,
            onDismiss = onDismissBugReport,
            onSubmit = onSubmitBugReport,
        )
    }
}

@Composable
private fun SettingsContentScreen(
    content: SettingsUiState.Content,
    onSlotTimeChange: (slotIndex: Int, timeOfDayMinutes: Int) -> Unit,
    onQuestionWordingModeChanged: (QuestionWordingMode) -> Unit,
    onOpenNotifications: () -> Unit,
    onTogglePauseState: () -> Unit,
    onOpenBugReport: () -> Unit,
    onBack: () -> Unit,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
    onBackupSetup: () -> Unit,
    onBackupNow: () -> Unit,
    onBackupChangeFolder: () -> Unit,
    onBackupReconnect: () -> Unit,
    onBackupDisable: () -> Unit,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
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
                            enabled = true,
                            onClick = { pickerSlotIndex = slot.slotIndex },
                        )
                    }
                    // 09.08.2026 Post-release fixes cursor by Me4Hik START - schedule autosave, no Save button
                    if (content.isSavingSchedule) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag(SettingsTestTags.SETTINGS_SCHEDULE_PROGRESS),
                            strokeWidth = 2.dp,
                        )
                    }
                    // 09.08.2026 Post-release fixes cursor by Me4Hik END
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

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(SettingsTestTags.SETTINGS_WORDING_SECTION),
            ) {
                PracticeSectionHeader(
                    title = stringResource(R.string.settings_wording_section),
                    icon = Icons.Outlined.Translate,
                )
                PracticeSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            Triple(
                                QuestionWordingMode.MASCULINE,
                                R.string.settings_wording_masculine,
                                SettingsTestTags.SETTINGS_WORDING_MASCULINE,
                            ),
                            Triple(
                                QuestionWordingMode.FEMININE,
                                R.string.settings_wording_feminine,
                                SettingsTestTags.SETTINGS_WORDING_FEMININE,
                            ),
                            Triple(
                                QuestionWordingMode.NEUTRAL,
                                R.string.settings_wording_neutral,
                                SettingsTestTags.SETTINGS_WORDING_NEUTRAL,
                            ),
                        ).forEach { (mode, labelRes, tag) ->
                            val selected = content.questionWordingMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        enabled = !content.isChangingQuestionWording,
                                        role = Role.RadioButton,
                                        onClick = { onQuestionWordingModeChanged(mode) },
                                    )
                                    .padding(vertical = 4.dp)
                                    .testTag(tag),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    enabled = !content.isChangingQuestionWording,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentViolet,
                                        unselectedColor = TextMuted,
                                    ),
                                )
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
                content.wordingError?.let {
                    Text(
                        text = stringResource(R.string.settings_wording_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PracticeSurface {
                PracticeGlassActionRow(
                    title = stringResource(R.string.settings_notifications_entry),
                    icon = Icons.Outlined.Notifications,
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_NOTIFICATIONS_ENTRY),
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

            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
            SettingsBackupSection(
                backup = content.backup,
                onSetup = onBackupSetup,
                onBackupNow = onBackupNow,
                onChangeFolder = onBackupChangeFolder,
                onReconnect = onBackupReconnect,
                onDisable = onBackupDisable,
            )
            // 10.08.2026 Post-release fixes cursor by Me4Hik END

            PracticeSurface {
                PracticeGlassActionRow(
                    title = stringResource(R.string.settings_bug_report),
                    icon = Icons.Outlined.BugReport,
                    onClick = onOpenBugReport,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BUG_REPORT),
                )
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
