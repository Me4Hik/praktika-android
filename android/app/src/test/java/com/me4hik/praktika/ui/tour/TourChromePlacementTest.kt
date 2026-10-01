package com.me4hik.praktika.ui.tour

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourChromePlacementTest {

    @Test
    fun highTarget_withMoreSpaceBelow_placesBelow() {
        val hole = Rect(40f, 80f, 360f, 220f)
        assertTrue(
            TourChromePlacement.placeBelow(
                holeInWindow = hole,
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
    }

    @Test
    fun lowTarget_withMoreSpaceAbove_placesAbove() {
        val hole = Rect(40f, 560f, 360f, 720f)
        assertFalse(
            TourChromePlacement.placeBelow(
                holeInWindow = hole,
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
    }

    @Test
    fun nullHole_placesBelow() {
        assertTrue(
            TourChromePlacement.placeBelow(
                holeInWindow = null,
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
    }

    @Test
    fun tallUpperArchiveLikeHole_placesBelow_soTopContentStaysClear() {
        // Narrow hub / days list: hole starts high and is tall; chrome must go below.
        val hole = Rect(24f, 140f, 360f, 620f)
        assertTrue(
            TourChromePlacement.placeBelow(
                holeInWindow = hole,
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
    }

    @Test
    fun chosenSide_doesNotFullyCoverHoleWhenEitherSideFits() {
        val overlay = 800f
        val chrome = 180f
        val highHole = Rect(20f, 60f, 340f, 200f)
        assertTrue(TourChromePlacement.placeBelow(highHole, overlay, chrome))
        // Below placement: free space under hole can host chrome without entering hole.
        assertTrue(overlay - highHole.bottom >= chrome)

        val lowHole = Rect(20f, 580f, 340f, 740f)
        assertFalse(TourChromePlacement.placeBelow(lowHole, overlay, chrome))
        assertTrue(lowHole.top >= chrome)
    }

    @Test
    fun smallTopBackTarget_stillPlacesBelow() {
        val hole = Rect(8f, 48f, 56f, 96f)
        assertTrue(
            TourChromePlacement.placeBelow(
                holeInWindow = hole,
                overlayHeightPx = 800f,
                estimatedChromeHeightPx = 200f,
            ),
        )
    }
}
