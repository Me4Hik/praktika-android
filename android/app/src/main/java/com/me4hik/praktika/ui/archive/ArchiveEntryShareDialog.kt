// 07.08.2026 Stage 21 Share cursor by Me4Hik START - dialog выбора text share для entry
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.me4hik.praktika.R

@Composable
fun ArchiveEntryShareDialog(
    onDismiss: () -> Unit,
    onQuestionOnly: () -> Unit,
    onQuestionAndAnswer: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(ArchiveTestTags.ENTRY_SHARE_DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.archive_entry_share_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ArchiveTestTags.ENTRY_SHARE_QUESTION_ONLY),
                    onClick = onQuestionOnly,
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.archive_entry_share_question_only),
                    )
                }
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ArchiveTestTags.ENTRY_SHARE_QUESTION_AND_ANSWER),
                    onClick = onQuestionAndAnswer,
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.archive_entry_share_question_and_answer),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(ArchiveTestTags.ENTRY_SHARE_CANCEL),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.archive_entry_share_cancel))
            }
        },
    )
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
