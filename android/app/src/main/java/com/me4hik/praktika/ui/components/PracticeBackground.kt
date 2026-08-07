// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - shared dark ambient background
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.me4hik.praktika.ui.theme.AccentDeepBlue
import com.me4hik.praktika.ui.theme.GlowAmber
import com.me4hik.praktika.ui.theme.GlowViolet
import com.me4hik.praktika.ui.theme.NavyBackground

enum class PracticeBackgroundStyle {
    Question,
    Onboarding,
    Home,
    Answer,
    Archive,
    Subdued,
}

@Composable
fun PracticeBackground(
    modifier: Modifier = Modifier,
    style: PracticeBackgroundStyle = PracticeBackgroundStyle.Question,
    heroGlowCenterFractionY: Float = 0.54f,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = NavyBackground)

        val topHazeAlpha = when (style) {
            PracticeBackgroundStyle.Onboarding -> 0.08f to 0.06f
            PracticeBackgroundStyle.Home -> 0.05f to 0.04f
            PracticeBackgroundStyle.Answer -> 0.04f to 0.03f
            PracticeBackgroundStyle.Archive -> 0.03f to 0.02f
            PracticeBackgroundStyle.Subdued -> 0.04f to 0.03f
            PracticeBackgroundStyle.Question -> 0.06f to 0.04f
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    AccentDeepBlue.copy(alpha = topHazeAlpha.first),
                    GlowViolet.copy(alpha = topHazeAlpha.second),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.28f,
            ),
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    NavyBackground.copy(alpha = 0.95f),
                    NavyBackground,
                    NavyBackground.copy(alpha = 0.92f),
                ),
                startY = 0f,
                endY = size.height * 0.35f,
            ),
        )

        if (style != PracticeBackgroundStyle.Subdued && style != PracticeBackgroundStyle.Archive) {
            val heroCenterY = when (style) {
                PracticeBackgroundStyle.Onboarding -> size.height * 0.20f
                PracticeBackgroundStyle.Home -> size.height * 0.30f
                PracticeBackgroundStyle.Answer -> size.height * 0.14f
                else -> size.height * heroGlowCenterFractionY
            }
            val heroCenter = when (style) {
                PracticeBackgroundStyle.Answer -> Offset(size.width * 0.82f, heroCenterY)
                else -> Offset(size.width * 0.5f, heroCenterY)
            }
            val heroRadiusScale = when (style) {
                PracticeBackgroundStyle.Onboarding -> 0.52f
                PracticeBackgroundStyle.Home -> 0.50f
                PracticeBackgroundStyle.Answer -> 0.28f
                else -> 0.58f
            }
            val heroAlpha = when (style) {
                PracticeBackgroundStyle.Onboarding -> 0.16f
                PracticeBackgroundStyle.Home -> 0.14f
                PracticeBackgroundStyle.Answer -> 0.08f
                else -> 0.12f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlowViolet.copy(alpha = heroAlpha),
                        AccentDeepBlue.copy(alpha = heroAlpha * 0.4f),
                        Color.Transparent,
                    ),
                    center = heroCenter,
                    radius = size.width * heroRadiusScale,
                ),
                radius = size.width * heroRadiusScale,
                center = heroCenter,
            )

            when (style) {
                PracticeBackgroundStyle.Question -> {
                    val warmCore = Offset(size.width * 0.52f, size.height * (heroGlowCenterFractionY + 0.04f))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlowAmber.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                            center = warmCore,
                            radius = size.width * 0.20f,
                        ),
                        radius = size.width * 0.20f,
                        center = warmCore,
                    )
                }
                PracticeBackgroundStyle.Home -> {
                    val warmCore = Offset(size.width * 0.52f, heroCenterY + size.height * 0.02f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlowAmber.copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                            center = warmCore,
                            radius = size.width * 0.16f,
                        ),
                        radius = size.width * 0.16f,
                        center = warmCore,
                    )
                }
                PracticeBackgroundStyle.Answer -> {
                    val warmCore = Offset(size.width * 0.80f, heroCenterY + size.height * 0.01f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlowAmber.copy(alpha = 0.02f),
                                Color.Transparent,
                            ),
                            center = warmCore,
                            radius = size.width * 0.08f,
                        ),
                        radius = size.width * 0.08f,
                        center = warmCore,
                    )
                }
                else -> Unit
            }
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    NavyBackground.copy(alpha = 0.18f),
                    NavyBackground.copy(alpha = 0.55f),
                    NavyBackground.copy(alpha = 0.88f),
                ),
                startY = size.height * 0.78f,
                endY = size.height,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    NavyBackground.copy(alpha = 0.45f),
                ),
                center = Offset(size.width * 0.5f, size.height * 0.45f),
                radius = size.width * 0.95f,
            ),
        )
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
