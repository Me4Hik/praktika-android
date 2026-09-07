package com.me4hik.praktika.ui.tour

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

object TourOverlayTestTags {
    const val ROOT = "tour_overlay_root"
    const val SCRIM = "tour_scrim"
    const val HOLE_BLOCKER = "tour_hole_blocker"
    const val CHROME = "tour_chrome"
    const val SKIP = "tour_skip"
    const val EXIT = "tour_exit"
    const val NEXT = "tour_next"
    const val TIP = "tour_tip"
    const val PROGRESS = "tour_progress"
}

@Composable
fun TourSpotlightLayer(
    holeInWindow: Rect?,
    blockHole: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }

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

        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (holeLocal != null) {
                    addRoundRect(RoundRect(holeLocal, CornerRadius(24f, 24f)))
                }
            }
            drawPath(path, Color.Black.copy(alpha = 0.62f))
            if (holeLocal != null) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(holeLocal.left, holeLocal.top),
                    size = Size(holeLocal.width, holeLocal.height),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = 3f),
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
