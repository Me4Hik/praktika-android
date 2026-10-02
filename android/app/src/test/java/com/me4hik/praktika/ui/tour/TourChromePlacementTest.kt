package com.me4hik.praktika.ui.tour

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourChromePlacementTest {

    @Test
    fun alwaysTop_regardlessOfHole() {
        assertFalse(TourChromePlacement.placeBelow(null, 800f, 200f))
        assertFalse(
            TourChromePlacement.placeBelow(
                holeInWindow = Rect(40f, 80f, 360f, 220f),
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
        assertFalse(
            TourChromePlacement.placeBelow(
                holeInWindow = Rect(40f, 560f, 360f, 720f),
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
        assertFalse(
            TourChromePlacement.placeBelow(
                holeInWindow = Rect(24f, 140f, 360f, 650f),
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
    }

    @Test
    fun topAlways_flag() {
        assertTrue(TourChromePlacement.isTopAlways())
    }

    @Test
    fun targetInset_coversOuterPadPlusChromeEstimate() {
        assertTrue(TourChromePlacement.TOP_TARGET_INSET_DP >= 200f)
        assertTrue(
            TourChromePlacement.TOP_TARGET_INSET_DP ==
                TourChromePlacement.TOP_OUTER_PADDING_DP +
                TourChromePlacement.DEFAULT_ESTIMATED_CHROME_HEIGHT_DP,
        )
    }
}
