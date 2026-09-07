package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourSpotlightComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hole_allowsUnderlyingClick_scrimBlocksNeighbor() {
        var targetClicks = 0
        var neighborClicks = 0
        composeRule.setContent {
            PraktikaTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "target",
                        modifier = Modifier
                            .offset(x = 40.dp, y = 80.dp)
                            .size(120.dp, 48.dp)
                            .testTag("underlying_target")
                            .clickable { targetClicks++ },
                    )
                    Text(
                        text = "neighbor",
                        modifier = Modifier
                            .offset(x = 40.dp, y = 200.dp)
                            .size(120.dp, 48.dp)
                            .testTag("underlying_neighbor")
                            .clickable { neighborClicks++ },
                    )
                    TourSpotlightLayer(
                        holeInWindow = Rect(40f, 80f, 280f, 176f),
                        blockHole = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("underlying_target").performClick()
        composeRule.onNodeWithTag("underlying_neighbor").performClick()
        assertEquals(1, targetClicks)
        assertEquals(0, neighborClicks)
    }

    @Test
    fun showOnly_holeBlocker_preventsTargetClick_andNextAdvances() {
        var targetClicks by mutableIntStateOf(0)
        var nextClicks = 0
        composeRule.setContent {
            PraktikaTheme {
                var clicks by remember { mutableIntStateOf(0) }
                targetClicks = clicks
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "blocked",
                        modifier = Modifier
                            .offset(40.dp, 80.dp)
                            .size(120.dp, 48.dp)
                            .testTag("show_only_target")
                            .clickable { clicks++ },
                    )
                    TourSpotlightLayer(
                        holeInWindow = Rect(40f, 80f, 280f, 176f),
                        blockHole = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = "next",
                        modifier = Modifier
                            .offset(40.dp, 400.dp)
                            .testTag(TourOverlayTestTags.NEXT)
                            .clickable { nextClicks++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("show_only_target").performClick()
        assertEquals(0, targetClicks)
        composeRule.onNodeWithTag(TourOverlayTestTags.NEXT).assertIsDisplayed().performClick()
        assertEquals(1, nextClicks)
    }
}
