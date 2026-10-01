package com.me4hik.praktika.ui.tour

import androidx.compose.ui.geometry.Rect

/**
 * Pure placement decision for tour chrome relative to the spotlight hole.
 *
 * Prefers the side where the coachmark fully fits in free space; when neither
 * side fits, keeps the top of a high-starting target readable by placing below.
 */
object TourChromePlacement {
    /** Default estimate: progress + ~3-line tip + actions + card/outer padding. */
    const val DEFAULT_ESTIMATED_CHROME_HEIGHT_DP = 200f

    fun placeBelow(
        holeInWindow: Rect?,
        overlayHeightPx: Float,
        estimatedChromeHeightPx: Float,
    ): Boolean {
        if (holeInWindow == null || overlayHeightPx <= 0f) return true

        val spaceAbove = holeInWindow.top.coerceAtLeast(0f)
        val spaceBelow = (overlayHeightPx - holeInWindow.bottom).coerceAtLeast(0f)
        val need = estimatedChromeHeightPx.coerceAtLeast(1f)

        val fitsAbove = spaceAbove >= need
        val fitsBelow = spaceBelow >= need

        return when {
            fitsBelow && !fitsAbove -> true
            fitsAbove && !fitsBelow -> false
            fitsAbove && fitsBelow -> spaceBelow >= spaceAbove
            // Neither side fully fits: if the hole starts in the upper portion,
            // place below so the top of the explained content stays visible.
            holeInWindow.top < overlayHeightPx * 0.40f -> true
            else -> spaceBelow >= spaceAbove
        }
    }
}
