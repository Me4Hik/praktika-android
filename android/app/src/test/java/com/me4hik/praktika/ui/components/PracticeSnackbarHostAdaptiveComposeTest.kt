// 03.10.2026 Snackbar compact layout cursor by Me4Hik START - compact/wide/info host geometry
// 03.10.2026 Snackbar one-row compact cursor by Me4Hik START - one-row check + short action
package com.me4hik.praktika.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PracticeSnackbarHostAdaptiveComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var textMeasurer: TextMeasurer
    private lateinit var bodyLarge: TextStyle
    private lateinit var labelLarge: TextStyle

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun compactAction_oneRow_checkMessageAction_noDismissNoActionZone() {
        showActionSnackbar()

        composeRule.onNodeWithTag(PracticeSnackbarTestTags.CHECK).assertIsDisplayed()
        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        composeRule.onNodeWithText("Смотреть").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeSnackbarTestTags.ACTION).assertHasClickAction()

        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.DISMISS)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.ACTION_ZONE)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Посмотреть историю").fetchSemanticsNodes().size,
        )

        val message = bounds(PracticeSnackbarTestTags.MESSAGE)
        val action = bounds(PracticeSnackbarTestTags.ACTION)
        val check = bounds(PracticeSnackbarTestTags.CHECK)
        val container = bounds(PracticeSnackbarTestTags.CONTAINER)
        val density = composeRule.density

        val messageBaselineY = textBaselineYInRoot(
            tag = PracticeSnackbarTestTags.MESSAGE,
            text = "Ответ сохранён",
            style = bodyLarge,
        )
        val actionBaselineY = textBaselineYInRoot(
            tag = PracticeSnackbarTestTags.ACTION,
            text = "Смотреть",
            style = labelLarge,
            centerInBounds = action.height + 0.5f >= with(density) { 48.dp.toPx() },
        )
        val baselineDeltaPx = abs(messageBaselineY - actionBaselineY)
        val baselineTolerancePx = with(density) { 1.dp.toPx() }
        assertTrue(
            "Expected message/action firstBaseline delta <= 1.dp, " +
                "deltaPx=$baselineDeltaPx, messageBaseline=$messageBaselineY, " +
                "actionBaseline=$actionBaselineY, actionHeight=${action.height}",
            baselineDeltaPx <= baselineTolerancePx,
        )

        val checkMessageCenterTolerancePx = with(density) { 2.dp.toPx() }
        assertTrue(
            "Expected check centerY ≈ message centerY, " +
                "dy=${abs(check.center.y - message.center.y)}",
            abs(check.center.y - message.center.y) <= checkMessageCenterTolerancePx,
        )

        val cardCenterTolerancePx = with(density) { 3.dp.toPx() }
        assertTrue(
            "Expected message optically near vertical center of card, " +
                "dy(container,message)=${abs(container.center.y - message.center.y)}",
            abs(container.center.y - message.center.y) <= cardCenterTolerancePx,
        )

        val minActionHeightPx = with(density) { 48.dp.toPx() }
        assertTrue(
            "Expected action hit target height >= 48.dp, height=${action.height}",
            action.height + 0.5f >= minActionHeightPx,
        )

        assertTrue(
            "Expected action to the right of message without overlap, " +
                "message.right=${message.right}, action.left=${action.left}",
            action.left >= message.right - 2f,
        )
        assertTrue(
            "Expected check left of message, check.right=${check.right}, message.left=${message.left}",
            message.left >= check.right - 2f,
        )

        // Compact card ≈ 48.dp content + 6+6.dp surface padding ≈ 60.dp.
        val maxCompactHeightPx = with(density) { 64.dp.toPx() }
        assertTrue(
            "Expected compact card height after 6.dp vertical padding, height=${container.height}",
            container.height <= maxCompactHeightPx,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "w600dp-h800dp")
    fun wideAction_keepsHorizontalRowWithoutExtraStackHeight() {
        showActionSnackbar()

        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        composeRule.onNodeWithText("Посмотреть историю").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeSnackbarTestTags.DISMISS).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.ACTION_ZONE)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.CHECK)
                .fetchSemanticsNodes()
                .size,
        )

        val message = bounds(PracticeSnackbarTestTags.MESSAGE)
        val action = bounds(PracticeSnackbarTestTags.ACTION)
        val container = bounds(PracticeSnackbarTestTags.CONTAINER)
        val density = composeRule.density

        assertTrue(
            "Expected horizontal layout on wide width, message.right=${message.right}, action.left=${action.left}",
            action.left >= message.right - 2f,
        )
        val centersClose =
            abs(message.center.y - action.center.y) <= with(density) { 12.dp.toPx() }
        assertTrue("Expected message/action vertically aligned in one row", centersClose)

        val maxSingleRowHeightPx = with(density) { 96.dp.toPx() }
        assertTrue(
            "Expected compact single-row height on wide, height=${container.height}",
            container.height <= maxSingleRowHeightPx,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun infoOnly_staysHorizontalEvenOnCompactWidth() {
        showInfoSnackbar()

        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeSnackbarTestTags.MESSAGE).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.ACTION).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.ACTION_ZONE)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.DISMISS).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.CHECK).fetchSemanticsNodes().size,
        )

        val container = bounds(PracticeSnackbarTestTags.CONTAINER)
        val density = composeRule.density
        val maxInfoHeightPx = with(density) { 88.dp.toPx() }
        assertTrue(
            "Info-only must not use stacked action layout height, height=${container.height}",
            container.height <= maxInfoHeightPx,
        )
    }

    private fun showActionSnackbar() {
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            PraktikaTheme {
                textMeasurer = rememberTextMeasurer()
                bodyLarge = MaterialTheme.typography.bodyLarge
                labelLarge = MaterialTheme.typography.labelLarge
                Box(modifier = Modifier.fillMaxSize()) {
                    PracticeSnackbarHost(hostState = hostState)
                }
            }
            LaunchedEffect(hostState) {
                yield()
                hostState.showPracticeActionSnackbar(
                    message = "Ответ сохранён",
                    actionLabel = "Посмотреть историю",
                    compactActionLabel = "Смотреть",
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.CONTAINER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun showInfoSnackbar() {
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            PraktikaTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    PracticeSnackbarHost(hostState = hostState)
                }
            }
            LaunchedEffect(hostState) {
                yield()
                hostState.showPracticeInfoSnackbar(message = "Ответ сохранён")
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(PracticeSnackbarTestTags.CONTAINER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun bounds(tag: String): Rect {
        return composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
    }

    /**
     * Prefer live FirstBaseline from layout coordinates when available.
     * Robolectric often returns [AlignmentLine.Unspecified]; fall back to
     * TextMeasurer firstBaseline, optionally vertically centered in the node bounds.
     */
    private fun textBaselineYInRoot(
        tag: String,
        text: String,
        style: TextStyle,
        centerInBounds: Boolean = false,
    ): Float {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val coords = node.layoutInfo.coordinates
        val liveBaseline = coords[FirstBaseline]
        if (liveBaseline != AlignmentLine.Unspecified) {
            return coords.localToRoot(Offset(0f, liveBaseline.toFloat())).y
        }
        val layout: TextLayoutResult = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        val box = node.boundsInRoot
        val topOffset = if (centerInBounds) {
            ((box.height - layout.size.height) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
        return box.top + topOffset + layout.firstBaseline
    }
}
// 03.10.2026 Snackbar one-row compact cursor by Me4Hik END
// 03.10.2026 Snackbar compact layout cursor by Me4Hik END
