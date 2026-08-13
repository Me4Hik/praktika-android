// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.settings.BugReportResultKind

@Composable
fun BugReportDialog(
    bugReportState: BugReportUiState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    val isSending = bugReportState is BugReportUiState.Sending
    val completedMessage = when ((bugReportState as? BugReportUiState.Completed)?.result) {
        BugReportResultKind.REMOTE_SENT -> stringResource(R.string.settings_bug_report_sent)
        BugReportResultKind.SAVED_LOCALLY -> stringResource(R.string.settings_bug_report_saved_locally)
        BugReportResultKind.SEND_FAILED -> stringResource(R.string.settings_bug_report_send_failed)
        null -> null
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSending) {
                onDismiss()
            }
        },
        title = {
            Text(text = stringResource(R.string.settings_bug_report_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_bug_report_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { value ->
                        comment = value.take(500)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag(SettingsTestTags.SETTINGS_BUG_REPORT_COMMENT),
                    enabled = !isSending && completedMessage == null,
                    placeholder = {
                        Text(text = stringResource(R.string.settings_bug_report_comment_hint))
                    },
                    minLines = 3,
                )
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag(SettingsTestTags.SETTINGS_BUG_REPORT_PROGRESS),
                        strokeWidth = 2.dp,
                    )
                }
                completedMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag(SettingsTestTags.SETTINGS_BUG_REPORT_RESULT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            if (completedMessage == null) {
                TextButton(
                    onClick = { onSubmit(comment.trim()) },
                    enabled = !isSending,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BUG_REPORT_SEND),
                ) {
                    Text(text = stringResource(R.string.settings_bug_report_send))
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BUG_REPORT_CLOSE),
                ) {
                    Text(text = stringResource(R.string.settings_bug_report_close))
                }
            }
        },
        dismissButton = {
            if (completedMessage == null) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSending,
                    modifier = Modifier.testTag(SettingsTestTags.SETTINGS_BUG_REPORT_CANCEL),
                ) {
                    Text(text = stringResource(R.string.settings_bug_report_cancel))
                }
            }
        },
    )
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
