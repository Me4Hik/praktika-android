// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - dialog выбора формата export
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.export.ExportFormat

@Composable
fun ArchiveExportFormatDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (ExportFormat) -> Unit,
    // 07.08.2026 Stage 21 Share cursor by Me4Hik START - параметризация title для share reuse
    titleRes: Int = R.string.archive_export_format_dialog_title,
    dialogTestTag: String = ArchiveTestTags.EXPORT_FORMAT_DIALOG,
    // 07.08.2026 Stage 21 Share cursor by Me4Hik END
) {
    AlertDialog(
        modifier = Modifier.testTag(dialogTestTag),
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(titleRes))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ArchiveTestTags.EXPORT_FORMAT_MARKDOWN),
                    onClick = { onFormatSelected(ExportFormat.MARKDOWN) },
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.archive_export_format_markdown),
                    )
                }
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ArchiveTestTags.EXPORT_FORMAT_CSV),
                    onClick = { onFormatSelected(ExportFormat.CSV) },
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.archive_export_format_csv),
                    )
                }
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ArchiveTestTags.EXPORT_FORMAT_PDF),
                    onClick = { onFormatSelected(ExportFormat.PDF) },
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.archive_export_format_pdf),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(ArchiveTestTags.EXPORT_FORMAT_CANCEL),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.archive_export_format_cancel))
            }
        },
    )
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
