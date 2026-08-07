// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - subtle archive separator
package com.me4hik.praktika.ui.archive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.AccentViolet
import com.me4hik.praktika.ui.theme.GlowAmber

@Composable
fun ArchiveDecorativeSeparator(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
    ) {
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.5f
        drawCircle(
            color = AccentViolet.copy(alpha = 0.22f),
            radius = 2.dp.toPx(),
            center = Offset(centerX, centerY),
        )
        drawCircle(
            color = GlowAmber.copy(alpha = 0.14f),
            radius = 1.dp.toPx(),
            center = Offset(centerX - 10.dp.toPx(), centerY),
        )
        drawCircle(
            color = GlowAmber.copy(alpha = 0.14f),
            radius = 1.dp.toPx(),
            center = Offset(centerX + 10.dp.toPx(), centerY),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(centerX - 36.dp.toPx(), centerY),
            end = Offset(centerX - 14.dp.toPx(), centerY),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(centerX + 14.dp.toPx(), centerY),
            end = Offset(centerX + 36.dp.toPx(), centerY),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
