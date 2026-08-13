// 04.08.2026 Reminder App cursor by Me4Hik START - экран знакомства
// 05.08.2026 Main Screen cursor by Me4Hik START - запуск практики через ViewModel
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - полноценный onboarding UI
package com.me4hik.praktika.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.PracticeBackground
import com.me4hik.praktika.ui.components.PracticeBackgroundStyle
import com.me4hik.praktika.ui.components.PracticeHeroAccent
import com.me4hik.praktika.ui.components.PracticePrimaryButton
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.components.ScheduleSlotRow
import com.me4hik.praktika.ui.practice.OnboardingScheduleError
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.practice.PracticeUiError
import com.me4hik.praktika.ui.practice.PracticeUiState
import com.me4hik.praktika.ui.practice.onboardingSlotTestTag
import com.me4hik.praktika.ui.restore.ProductionRestoreTestTags
import com.me4hik.praktika.ui.theme.TextQuestionSoft
import com.me4hik.praktika.ui.theme.TextSecondary
import com.me4hik.praktika.ui.theme.TitleSerifStyle

@Composable
fun OnboardingScreen(
    state: PracticeUiState.NotStarted,
    onSlotTimeChange: (slotIndex: Int, timeOfDayMinutes: Int) -> Unit,
    onStartPractice: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    var pickerSlotIndex by remember { mutableIntStateOf(-1) }

    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - onboarding glass layout
    Box(modifier = Modifier.fillMaxSize()) {
        PracticeBackground(style = PracticeBackgroundStyle.Onboarding)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .testTag(PracticeTestTags.ONBOARDING_SCREEN),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            PracticeHeroAccent(widthFraction = 0.38f)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = TitleSerifStyle,
                color = TextQuestionSoft,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onboarding_description),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag(PracticeTestTags.ONBOARDING_DESCRIPTION),
            )
            Spacer(modifier = Modifier.height(24.dp))
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - onboarding primary CTA above schedule
            val startEnabled = !state.isScheduleLoading &&
                state.isScheduleValid &&
                !state.isStarting
            PracticePrimaryButton(
                text = stringResource(R.string.onboarding_start_practice),
                onClick = onStartPractice,
                enabled = startEnabled,
                showProgress = state.isStarting,
                progressTestTag = PracticeTestTags.ONBOARDING_START_PROGRESS,
                modifier = Modifier.testTag(PracticeTestTags.ONBOARDING_START),
            )
            TextButton(
                onClick = onRestoreBackup,
                enabled = !state.isStarting && !state.isScheduleLoading,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_restore_backup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.startError?.let { error ->
                Text(
                    text = onboardingStartErrorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag(PracticeTestTags.ONBOARDING_START_ERROR),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PracticeTestTags.ONBOARDING_SCHEDULE),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_schedule_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (state.isScheduleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    PracticeSurface {
                        state.slots.forEachIndexed { index, slot ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                )
                            }
                            ScheduleSlotRow(
                                slotLabel = stringResource(R.string.settings_slot_label, slot.slotIndex),
                                timeText = slot.timeText,
                                changeLabel = stringResource(R.string.settings_slot_change),
                                enabled = !state.isStarting,
                                onClick = { pickerSlotIndex = slot.slotIndex },
                                modifier = Modifier.testTag(onboardingSlotTestTag(slot.slotIndex)),
                                contentDescription = stringResource(
                                    R.string.settings_slot_content_description,
                                    slot.slotIndex,
                                    slot.timeText,
                                ),
                            )
                        }
                    }
                }
                state.scheduleError?.let { error ->
                    Text(
                        text = onboardingScheduleErrorText(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(PracticeTestTags.ONBOARDING_SCHEDULE_ERROR),
                    )
                }
            }
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END

    if (pickerSlotIndex > 0 && state.slots.isNotEmpty()) {
        val selectedSlot = state.slots.first { it.slotIndex == pickerSlotIndex }
        OnboardingTimePickerDialog(
            initialMinutes = selectedSlot.timeOfDayMinutes,
            onDismiss = { pickerSlotIndex = -1 },
            onConfirm = { minutes ->
                onSlotTimeChange(pickerSlotIndex, minutes)
                pickerSlotIndex = -1
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingTimePickerDialog(
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
        modifier = Modifier.testTag(PracticeTestTags.ONBOARDING_TIME_PICKER),
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
private fun onboardingScheduleErrorText(error: OnboardingScheduleError): String {
    return when (error) {
        OnboardingScheduleError.DUPLICATE_TIME ->
            stringResource(R.string.settings_schedule_duplicate_error)
    }
}

@Composable
private fun onboardingStartErrorText(error: PracticeUiError): String {
    return when (error) {
        PracticeUiError.START_FAILED -> stringResource(R.string.practice_error_start_failed)
        PracticeUiError.CORRUPTION -> stringResource(R.string.practice_error_corruption)
        PracticeUiError.LOAD_FAILED -> stringResource(R.string.practice_error_load_failed)
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
// 04.08.2026 Reminder App cursor by Me4Hik END
