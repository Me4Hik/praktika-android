// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - shared glass surface
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.AccentDeepBlue
import com.me4hik.praktika.ui.theme.CtaBorderBlue
import com.me4hik.praktika.ui.theme.CtaBorderLavender
import com.me4hik.praktika.ui.theme.ElevatedSurface
import com.me4hik.praktika.ui.theme.GlowViolet

@Composable
fun PracticeSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            ElevatedSurface.copy(alpha = 0.72f),
            ElevatedSurface.copy(alpha = 0.58f),
        ),
    )
    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            CtaBorderLavender.copy(alpha = 0.35f),
            GlowViolet.copy(alpha = 0.22f),
            CtaBorderBlue.copy(alpha = 0.30f),
        ),
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content,
    )
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
