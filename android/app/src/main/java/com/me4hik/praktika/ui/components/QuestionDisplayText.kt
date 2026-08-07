// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - serif question display text
package com.me4hik.praktika.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.me4hik.praktika.ui.theme.QuestionSerifStyle
import com.me4hik.praktika.ui.theme.TextQuestionSoft

@Composable
fun QuestionDisplayText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = QuestionSerifStyle,
        color = TextQuestionSoft,
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
