// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - QuestionScreen primary CTA
package com.me4hik.praktika.ui.question

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.me4hik.praktika.ui.components.PracticePrimaryButton

@Composable
fun QuestionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    PracticePrimaryButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        showProgress = showProgress,
    )
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
