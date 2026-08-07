// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - shared primary CTA button
package com.me4hik.praktika.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.CtaBlueSoft
import com.me4hik.praktika.ui.theme.CtaBorderBlue
import com.me4hik.praktika.ui.theme.CtaBorderLavender
import com.me4hik.praktika.ui.theme.CtaGlowSoft
import com.me4hik.praktika.ui.theme.CtaVioletBlueMid
import com.me4hik.praktika.ui.theme.CtaVioletSoft
import com.me4hik.praktika.ui.theme.TextPrimary

@Composable
fun PracticePrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    progressTestTag: String? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    val fillGradient = Brush.horizontalGradient(
        colors = listOf(
            CtaVioletSoft,
            CtaVioletBlueMid,
            CtaBlueSoft,
        ),
    )
    val disabledGradient = Brush.horizontalGradient(
        colors = listOf(
            CtaVioletSoft.copy(alpha = 0.35f),
            CtaVioletBlueMid.copy(alpha = 0.35f),
            CtaBlueSoft.copy(alpha = 0.35f),
        ),
    )
    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            CtaBorderLavender,
            CtaBorderBlue,
            CtaBorderBlue.copy(alpha = 0.45f),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                if (enabled) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CtaGlowSoft.copy(alpha = 0.14f),
                                CtaGlowSoft.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.maxDimension * 0.72f,
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
                        size = size.copy(
                            width = size.width + 10.dp.toPx(),
                            height = size.height + 6.dp.toPx(),
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(-5.dp.toPx(), -2.dp.toPx()),
                    )
                }
            }
            .clip(shape)
            .background(if (enabled) fillGradient else disabledGradient)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = TextPrimary.copy(alpha = 0.10f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .then(
                        if (progressTestTag != null) {
                            Modifier.testTag(progressTestTag)
                        } else {
                            Modifier
                        },
                    ),
                color = TextPrimary,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
