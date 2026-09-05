// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - archive journal entry card
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.components.MetadataText
import com.me4hik.praktika.ui.components.PracticeSurface
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.Destructive
import com.me4hik.praktika.ui.theme.HomeQuestionSerifStyle
import com.me4hik.praktika.ui.theme.TextPrimary

@Composable
fun ArchiveEntryCard(
    answerText: String,
    metadata: String,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    shareTestTag: String,
    deleteTestTag: String,
    modifier: Modifier = Modifier,
    questionText: String? = null,
    cycleLabel: String? = null,
    showShare: Boolean = true,
    showDelete: Boolean = true,
) {
    PracticeSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (questionText != null) {
                MetadataText(text = stringResource(R.string.archive_question_label))
                Text(
                    text = questionText,
                    style = HomeQuestionSerifStyle,
                    color = TextPrimary,
                )
            }
            MetadataText(text = stringResource(R.string.archive_answer_label))
            Text(
                text = answerText,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            MetadataText(text = metadata)
            if (cycleLabel != null) {
                MetadataText(text = cycleLabel)
            }
            if (showShare || showDelete) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showShare) {
                        TextButton(
                            onClick = onShare,
                            modifier = Modifier.testTag(shareTestTag),
                        ) {
                            Text(
                                text = stringResource(R.string.archive_share_action),
                                color = AccentViolet.copy(alpha = 0.90f),
                            )
                        }
                    }
                    if (showDelete) {
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag(deleteTestTag),
                        ) {
                            Text(
                                text = stringResource(R.string.archive_delete_action),
                                color = Destructive.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
