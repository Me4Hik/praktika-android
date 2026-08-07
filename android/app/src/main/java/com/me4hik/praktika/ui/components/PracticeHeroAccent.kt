// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - small decorative hero orb
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.theme.AccentDeepBlue
import com.me4hik.praktika.ui.theme.GlowAmber
import com.me4hik.praktika.ui.theme.GlowPeach
import com.me4hik.praktika.ui.theme.GlowViolet

@Composable
fun PracticeHeroAccent(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.38f,
    minHeight: androidx.compose.ui.unit.Dp = 100.dp,
    maxHeight: androidx.compose.ui.unit.Dp = 150.dp,
    glowAlphaMultiplier: Float = 1f,
    contentAlignment: Alignment = Alignment.TopCenter,
    showWarmCore: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight),
        contentAlignment = contentAlignment,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = when (contentAlignment) {
                Alignment.TopEnd, Alignment.CenterEnd, Alignment.BottomEnd ->
                    Offset(size.width * 0.72f, size.height * 0.48f)
                else -> Offset(size.width * 0.5f, size.height * 0.48f)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlowViolet.copy(alpha = 0.14f * glowAlphaMultiplier),
                        AccentDeepBlue.copy(alpha = 0.06f * glowAlphaMultiplier),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.width * 0.42f,
                ),
                radius = size.width * 0.42f,
                center = center,
            )
            if (showWarmCore) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GlowPeach.copy(alpha = 0.08f * glowAlphaMultiplier),
                            GlowAmber.copy(alpha = 0.04f * glowAlphaMultiplier),
                            Color.Transparent,
                        ),
                        center = Offset(center.x, center.y + size.height * 0.08f),
                        radius = size.width * 0.12f,
                    ),
                    radius = size.width * 0.12f,
                    center = Offset(center.x, center.y + size.height * 0.08f),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.question_hero_liquid_orb),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(widthFraction),
        )
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
