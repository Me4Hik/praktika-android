package com.me4hik.praktika.ui.tour

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Pulse accent for ManualAdvance chrome CTAs (Далее / Готово).
 * Does not change button measured size (scale via graphicsLayer only).
 */
@Composable
fun ManualAdvanceCueBox(
    enabled: Boolean,
    testTag: String,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val pulse = remember { Animatable(1f) }
    val strokeBoost = remember { Animatable(0f) }

    LaunchedEffect(enabled, reducedMotion) {
        pulse.snapTo(1f)
        strokeBoost.snapTo(0f)
        if (!enabled) return@LaunchedEffect
        if (reducedMotion) {
            strokeBoost.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            repeat(2) {
                pulse.animateTo(1.04f, tween(220, easing = LinearEasing))
                strokeBoost.animateTo(1f, tween(220, easing = LinearEasing))
                pulse.animateTo(1f, tween(220, easing = LinearEasing))
                strokeBoost.animateTo(0.35f, tween(220, easing = LinearEasing))
            }
            strokeBoost.animateTo(0f, tween(200, easing = LinearEasing))
            delay(5_000L)
        }
    }

    val scale = if (enabled && !reducedMotion) pulse.value else 1f
    val borderWidth = if (!enabled) {
        0.dp
    } else if (reducedMotion) {
        2.5.dp
    } else {
        (1.5f + strokeBoost.value * 2f).dp
    }
    val borderAlpha = if (!enabled) {
        0f
    } else if (reducedMotion) {
        1f
    } else {
        0.55f + strokeBoost.value * 0.45f
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) {
                    Modifier
                        .border(
                            width = borderWidth,
                            color = Color.White.copy(alpha = borderAlpha),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .testTag(testTag)
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
