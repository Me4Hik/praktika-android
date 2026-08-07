// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - shared metadata text
package com.me4hik.praktika.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.me4hik.praktika.ui.theme.TextMuted

@Composable
fun MetadataText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        modifier = modifier,
    )
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
