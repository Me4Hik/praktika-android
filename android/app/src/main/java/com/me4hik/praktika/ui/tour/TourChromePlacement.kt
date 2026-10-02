package com.me4hik.praktika.ui.tour

import androidx.compose.ui.geometry.Rect

/**
 * Tour chrome placement — fixed top for all phases (no bottom switching).
 *
 * Actionable targets must reveal below the top chrome band (bringIntoView inset /
 * tour-only spacer), not move the chrome.
 */
object TourChromePlacement {
    /** Default estimate: progress + ~3-line tip + actions + card/outer padding. */
    const val DEFAULT_ESTIMATED_CHROME_HEIGHT_DP = 200f

    /** Outer top padding applied around the chrome card in [TourChrome]. */
    const val TOP_OUTER_PADDING_DP = 48f

    /**
     * Vertical space reserved under the top of the window so actionable targets
     * can sit fully below fixed-top chrome (outer pad + estimated card).
     */
    const val TOP_TARGET_INSET_DP = TOP_OUTER_PADDING_DP + DEFAULT_ESTIMATED_CHROME_HEIGHT_DP

    /**
     * Always false → chrome aligns to top.
     * Hole arguments kept for call-site compatibility / tests.
     */
    @Suppress("UNUSED_PARAMETER")
    fun placeBelow(
        holeInWindow: Rect?,
        overlayHeightPx: Float,
        estimatedChromeHeightPx: Float,
    ): Boolean = false

    fun isTopAlways(): Boolean = true
}
