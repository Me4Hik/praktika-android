package com.me4hik.praktika.ui.tour

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

object TourOverlayTestTags {
    const val ROOT = "tour_overlay_root"
    const val SCRIM = "tour_scrim"
    const val HOLE_BLOCKER = "tour_hole_blocker"
    const val CHROME = "tour_chrome"
    const val SKIP = "tour_skip"
    const val EXIT = "tour_exit"
    const val NEXT = "tour_next"
    const val DONE = "tour_done"
    const val FINISH = "tour_finish"
    const val GATE_CONTINUE = "tour_gate_continue"
    const val GATE_FINISH = "tour_gate_finish"
    const val TIP = "tour_tip"
    const val PROGRESS = "tour_progress"
    const val ACTION_CUE = "tour_action_cue"
    const val MANUAL_ADVANCE_CUE = "tour_manual_advance_cue"
}

@Composable
fun TourSpotlightLayer(
    holeInWindow: Rect?,
    blockHole: Boolean,
    modifier: Modifier = Modifier,
    showActionCue: Boolean = false,
) {
    val density = LocalDensity.current
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
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
    val cueStrokeBoost = remember { Animatable(0f) }

    LaunchedEffect(showActionCue, holeInWindow, reducedMotion) {
        pulse.snapTo(1f)
        cueStrokeBoost.snapTo(0f)
        if (!showActionCue || holeInWindow == null) return@LaunchedEffect
        if (reducedMotion) {
            cueStrokeBoost.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            repeat(2) {
                pulse.animateTo(1.04f, tween(220, easing = LinearEasing))
                cueStrokeBoost.animateTo(1f, tween(220, easing = LinearEasing))
                pulse.animateTo(1f, tween(220, easing = LinearEasing))
                cueStrokeBoost.animateTo(0.35f, tween(220, easing = LinearEasing))
            }
            cueStrokeBoost.animateTo(0f, tween(200, easing = LinearEasing))
            delay(5_000L)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(TourOverlayTestTags.SCRIM)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                windowOrigin = Offset(b.left, b.top)
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val holeLocal = holeInWindow?.let { hole ->
            Rect(
                left = hole.left - windowOrigin.x,
                top = hole.top - windowOrigin.y,
                right = hole.right - windowOrigin.x,
                bottom = hole.bottom - windowOrigin.y,
            ).let { expandRect(it, with(density) { 8.dp.toPx() }) }
        }
        val pulseScale = if (showActionCue && !reducedMotion) pulse.value else 1f
        val strokeExtra = if (showActionCue) {
            if (reducedMotion) 2.5f else cueStrokeBoost.value * 2f
        } else {
            0f
        }
        val accentAlpha = if (showActionCue) {
            if (reducedMotion) 1f else 0.85f + cueStrokeBoost.value * 0.15f
        } else {
            0.85f
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showActionCue) Modifier.testTag(TourOverlayTestTags.ACTION_CUE) else Modifier,
                ),
        ) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (holeLocal != null) {
                    val pulsed = scaleRectAroundCenter(holeLocal, pulseScale)
                    addRoundRect(RoundRect(pulsed, CornerRadius(24f, 24f)))
                }
            }
            drawPath(path, Color.Black.copy(alpha = 0.62f))
            if (holeLocal != null) {
                val pulsed = scaleRectAroundCenter(holeLocal, pulseScale)
                drawRoundRect(
                    color = Color.White.copy(alpha = accentAlpha),
                    topLeft = Offset(pulsed.left, pulsed.top),
                    size = Size(pulsed.width, pulsed.height),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = 3f + strokeExtra),
                )
            }
        }

        if (holeLocal == null) {
            FullScreenBlocker()
        } else {
            val left = holeLocal.left.coerceIn(0f, widthPx)
            val top = holeLocal.top.coerceIn(0f, heightPx)
            val right = holeLocal.right.coerceIn(0f, widthPx)
            val bottom = holeLocal.bottom.coerceIn(0f, heightPx)

            if (top > 0f) {
                ScrimRegion(0f, 0f, widthPx, top)
            }
            if (bottom < heightPx) {
                ScrimRegion(0f, bottom, widthPx, heightPx - bottom)
            }
            if (left > 0f) {
                ScrimRegion(0f, top, left, max(0f, bottom - top))
            }
            if (right < widthPx) {
                ScrimRegion(right, top, widthPx - right, max(0f, bottom - top))
            }
            if (blockHole) {
                ScrimRegion(
                    left,
                    top,
                    max(0f, right - left),
                    max(0f, bottom - top),
                    testTag = TourOverlayTestTags.HOLE_BLOCKER,
                )
            }
        }
    }
}

@Composable
private fun FullScreenBlocker() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    )
}

@Composable
private fun ScrimRegion(
    offsetX: Float,
    offsetY: Float,
    width: Float,
    height: Float,
    testTag: String? = null,
) {
    if (width <= 0f || height <= 0f) return
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(
                width = with(density) { width.toDp() },
                height = with(density) { height.toDp() },
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    )
}

private fun expandRect(rect: Rect, amount: Float): Rect =
    Rect(rect.left - amount, rect.top - amount, rect.right + amount, rect.bottom + amount)

private fun scaleRectAroundCenter(rect: Rect, scale: Float): Rect {
    if (scale == 1f) return rect
    val cx = rect.center.x
    val cy = rect.center.y
    val hw = rect.width * scale / 2f
    val hh = rect.height * scale / 2f
    return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}
