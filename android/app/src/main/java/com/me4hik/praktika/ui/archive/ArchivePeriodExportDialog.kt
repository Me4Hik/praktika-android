// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - dialog выбора периода export
// 06.09.2026 Archive UX polish cursor by Me4Hik START - compact period dialog + single DatePicker
// 06.09.2026 Archive period bounds cursor by Me4Hik START - SelectableDates from answer bounds
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.MetadataText
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PeriodDateField {
    Start,
    End,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePeriodExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (startEpochDay: Long, endEpochDayInclusive: Long) -> Unit,
    answerDateBounds: ArchiveAnswerDateBounds? = null,
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
    var startEpochDay by remember(answerDateBounds, initialSelectedStartEpochDay) {
        mutableStateOf(
            ArchiveAnswerDateBounds.sanitizeInitialEpochDay(
                initialSelectedStartEpochDay,
                answerDateBounds,
            ),
        )
    }
    var endEpochDay by remember(answerDateBounds, initialSelectedEndEpochDay) {
        mutableStateOf(
            ArchiveAnswerDateBounds.sanitizeInitialEpochDay(
                initialSelectedEndEpochDay,
                answerDateBounds,
            ),
        )
    }
    var editingField by remember { mutableStateOf<PeriodDateField?>(null) }

    val canConfirm = answerDateBounds != null &&
        startEpochDay != null &&
        endEpochDay != null &&
        answerDateBounds.contains(startEpochDay!!) &&
        answerDateBounds.contains(endEpochDay!!) &&
        endEpochDay!! >= startEpochDay!!

    val selectableDates = remember(answerDateBounds) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val bounds = answerDateBounds ?: return false
                val epochDay = ArchivePeriodUtcDates.millisToEpochDay(utcTimeMillis)
                return bounds.contains(epochDay)
            }

            override fun isSelectableYear(year: Int): Boolean {
                val bounds = answerDateBounds ?: return false
                val minYear = LocalDate.ofEpochDay(bounds.earliestEpochDay).year
                val maxYear = LocalDate.ofEpochDay(bounds.latestEpochDay).year
                return year in minYear..maxYear
            }
        }
    }

    val yearRange = remember(answerDateBounds) {
        val bounds = answerDateBounds
        if (bounds == null) {
            IntRange(1970, 2100)
        } else {
            val minYear = LocalDate.ofEpochDay(bounds.earliestEpochDay).year
            val maxYear = LocalDate.ofEpochDay(bounds.latestEpochDay).year
            IntRange(minYear, maxYear)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(dialogTestTag),
        title = {
            Text(text = stringResource(titleRes))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PeriodDateRow(
                    label = stringResource(R.string.archive_export_period_start_date),
                    valueEpochDay = startEpochDay,
                    enabled = answerDateBounds != null,
                    onClick = { editingField = PeriodDateField.Start },
                    modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD_START),
                )
                PeriodDateRow(
                    label = stringResource(R.string.archive_export_period_end_date),
                    valueEpochDay = endEpochDay,
                    enabled = answerDateBounds != null,
                    onClick = { editingField = PeriodDateField.End },
                    modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD_END),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bounds = answerDateBounds ?: return@TextButton
                    val start = startEpochDay ?: return@TextButton
                    val end = endEpochDay ?: return@TextButton
                    if (bounds.contains(start) && bounds.contains(end) && end >= start) {
                        onConfirm(start, end)
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

    val boundsForPicker = answerDateBounds
    if (boundsForPicker != null) {
        editingField?.let { field ->
            key(field, boundsForPicker) {
                val selectedForField = when (field) {
                    PeriodDateField.Start -> startEpochDay
                    PeriodDateField.End -> endEpochDay
                }
                val fallbackMonth = ArchivePeriodUtcDates.epochDayToUtcMidnightMillis(
                    boundsForPicker.latestEpochDay,
                )
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedForField?.let(
                        ArchivePeriodUtcDates::epochDayToUtcMidnightMillis,
                    ),
                    initialDisplayedMonthMillis = selectedForField?.let(
                        ArchivePeriodUtcDates::epochDayToUtcMidnightMillis,
                    ) ?: initialDisplayedMonthMillis ?: fallbackMonth,
                    yearRange = yearRange,
                    selectableDates = selectableDates,
                )
                DatePickerDialog(
                    onDismissRequest = { editingField = null },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val millis = datePickerState.selectedDateMillis ?: return@TextButton
                                val epochDay = ArchivePeriodUtcDates.millisToEpochDay(millis)
                                if (!boundsForPicker.contains(epochDay)) {
                                    return@TextButton
                                }
                                when (field) {
                                    PeriodDateField.Start -> startEpochDay = epochDay
                                    PeriodDateField.End -> endEpochDay = epochDay
                                }
                                editingField = null
                            },
                            modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD_DATE_OK),
                        ) {
                            Text(text = stringResource(R.string.archive_export_period_date_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { editingField = null },
                            modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD_DATE_CANCEL),
                        ) {
                            Text(text = stringResource(R.string.archive_export_period_cancel))
                        }
                    },
                ) {
                    DatePicker(
                        state = datePickerState,
                        modifier = Modifier.testTag(ArchiveTestTags.EXPORT_PERIOD_PICKER),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodDateRow(
    label: String,
    valueEpochDay: Long?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valueText = if (valueEpochDay != null) {
        formatPeriodEpochDay(valueEpochDay)
    } else {
        stringResource(R.string.archive_export_period_date_unset)
    }
    PracticeSurface(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            MetadataText(text = valueText)
        }
    }
}

private fun formatPeriodEpochDay(epochDay: Long): String {
    return LocalDate.ofEpochDay(epochDay)
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
}
// 06.09.2026 Archive period bounds cursor by Me4Hik END
// 06.09.2026 Archive UX polish cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
