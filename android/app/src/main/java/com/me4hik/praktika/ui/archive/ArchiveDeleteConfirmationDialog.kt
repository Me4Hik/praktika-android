// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - confirmation dialog удаления Answer
package com.me4hik.praktika.ui.archive

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.theme.Destructive
import com.me4hik.praktika.ui.theme.ElevatedSurface
import com.me4hik.praktika.ui.theme.TextPrimary
import com.me4hik.praktika.ui.theme.TextSecondary

@Composable
fun ArchiveDeleteConfirmationDialog(
    deleteUiState: ArchiveDeleteUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - dark delete dialog
    val pendingAnswerId = deleteUiState.pendingDeleteAnswerId ?: return
    AlertDialog(
        onDismissRequest = {
            if (!deleteUiState.isDeleting) {
                onCancel()
            }
        },
        modifier = Modifier.testTag(ArchiveTestTags.DELETE_DIALOG),
        containerColor = ElevatedSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = stringResource(R.string.archive_delete_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.archive_delete_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !deleteUiState.isDeleting,
                modifier = Modifier.testTag("${ArchiveTestTags.DELETE_CONFIRM_PREFIX}$pendingAnswerId"),
            ) {
                Text(
                    text = stringResource(R.string.archive_delete_confirm),
                    color = Destructive,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !deleteUiState.isDeleting,
                modifier = Modifier.testTag(ArchiveTestTags.DELETE_CANCEL),
            ) {
                Text(
                    text = stringResource(R.string.archive_delete_cancel),
                    color = TextSecondary,
                )
            }
        },
    )
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
