// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - dialog выбора периода export
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePeriodExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (startEpochDay: Long, endEpochDayInclusive: Long) -> Unit,
    initialDisplayedMonthMillis: Long? = null,
    initialSelectedStartEpochDay: Long? = null,
    initialSelectedEndEpochDay: Long? = null,
    // 07.08.2026 Stage 21 Share cursor by Me4Hik START - параметризация period dialog для share reuse
    titleRes: Int = R.string.archive_export_period_dialog_title,
    confirmRes: Int = R.string.archive_export_period_confirm,
    dialogTestTag: String = ArchiveTestTags.EXPORT_PERIOD_DIALOG,
    confirmTestTag: String = ArchiveTestTags.EXPORT_PERIOD_CONFIRM,
    // 07.08.2026 Stage 21 Share cursor by Me4Hik END
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialSelectedStartEpochDay?.let(::epochDayToMillis),
        initialSelectedEndDateMillis = initialSelectedEndEpochDay?.let(::epochDayToMillis),
        initialDisplayedMonthMillis = initialDisplayedMonthMillis,
    )
    val canConfirm = dateRangePickerState.selectedStartDateMillis != null &&
        dateRangePickerState.selectedEndDateMillis != null

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(dialogTestTag),
        title = {
            Text(text = stringResource(titleRes))
        },
        text = {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .testTag(ArchiveTestTags.EXPORT_PERIOD_PICKER),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis ?: return@TextButton
                    val endMillis = dateRangePickerState.selectedEndDateMillis ?: return@TextButton
                    val startEpochDay = millisToEpochDay(startMillis)
                    val endEpochDay = millisToEpochDay(endMillis)
                    if (endEpochDay >= startEpochDay) {
                        onConfirm(startEpochDay, endEpochDay)
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.testTag(confirmTestTag),
            ) {
                Text(text = stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD_CANCEL),
            ) {
                Text(text = stringResource(R.string.archive_export_period_cancel))
            }
        },
    )
}

private fun millisToEpochDay(selectedMillis: Long): Long {
    return Instant.ofEpochMilli(selectedMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toEpochDay()
}

private fun epochDayToMillis(epochDay: Long): Long {
    return Instant.ofEpochSecond(epochDay * 86_400)
        .toEpochMilli()
}
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
