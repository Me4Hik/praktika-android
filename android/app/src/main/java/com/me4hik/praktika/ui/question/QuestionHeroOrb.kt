// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - QuestionScreen hero orb asset
package com.me4hik.praktika.ui.question

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.theme.AccentDeepBlue
import com.me4hik.praktika.ui.theme.GlowAmber
import com.me4hik.praktika.ui.theme.GlowPeach
import com.me4hik.praktika.ui.theme.GlowViolet

@Composable
fun QuestionHeroOrb(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroBreath")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 260.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val orbCenterX = size.width * 0.5f
            val orbCenterY = size.height * 0.44f
            val contactY = size.height * 0.62f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlowViolet.copy(alpha = 0.10f),
                        AccentDeepBlue.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    center = Offset(orbCenterX, orbCenterY),
                    radius = size.width * 0.48f,
                ),
                radius = size.width * 0.48f,
                center = Offset(orbCenterX, orbCenterY),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlowPeach.copy(alpha = 0.10f),
                        GlowAmber.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(orbCenterX, contactY),
                    radius = size.width * 0.14f,
                ),
                radius = size.width * 0.14f,
                center = Offset(orbCenterX, contactY),
            )

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        GlowViolet.copy(alpha = 0.08f),
                        AccentDeepBlue.copy(alpha = 0.07f),
                        GlowViolet.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    startX = 0f,
                    endX = size.width,
                ),
                topLeft = Offset(0f, contactY - size.width * 0.04f),
                size = Size(size.width, size.width * 0.10f),
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlowViolet.copy(alpha = 0.06f),
                        AccentDeepBlue.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    startY = contactY,
                    endY = contactY + size.height * 0.22f,
                ),
                topLeft = Offset(size.width * 0.08f, contactY),
                size = Size(size.width * 0.84f, size.height * 0.22f),
            )
        }
        Image(
            painter = painterResource(R.drawable.question_hero_liquid_orb),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .offset(y = (-4).dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
