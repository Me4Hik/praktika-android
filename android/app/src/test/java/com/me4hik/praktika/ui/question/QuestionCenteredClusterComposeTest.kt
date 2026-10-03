// 03.10.2026 Tablet-02 centered cluster fix cursor by Me4Hik START
package com.me4hik.praktika.ui.question

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.ui.QuestionScreen
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.practice.QuestionCommandState
import com.me4hik.praktika.ui.practice.QuestionUiModel
import com.me4hik.praktika.ui.practice.QuestionUiState
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestionCenteredClusterComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [28], qualifiers = "w600dp-h960dp")
    fun medium600x960_centeredCluster_orbFullyVisible_actionsBelow() {
        assertCenteredTabletGeometry()
    }

    @Test
    @Config(sdk = [28], qualifiers = "w560dp-h900dp")
    fun boundary560x900_centeredCluster_orbFullyVisible_actionsBelow() {
        assertCenteredTabletGeometry()
    }

    @Test
    @Config(sdk = [28], qualifiers = "w1280dp-h800dp")
    fun landscape1280x800_centeredCluster_noOverlap_scrollSafety() {
        setInteractiveQuestion()

        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_CENTERED_CLUSTER).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_HERO_ORB).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_ANSWER).assertIsDisplayed()

        val tolerancePx = with(composeRule.density) { 2.dp.toPx() }
        val answer = boundsPx(PracticeTestTags.QUESTION_ANSWER)
        val defer = boundsPx(PracticeTestTags.QUESTION_DEFER)
        val skip = boundsPx(PracticeTestTags.QUESTION_SKIP)
        val cluster = boundsPx(PracticeTestTags.QUESTION_CENTERED_CLUSTER)
        val content = boundsPx(PracticeTestTags.QUESTION_CENTERED_CONTENT)

        assertTrue(
            "actions must stay below centered cluster",
            answer.top + tolerancePx >= cluster.bottom,
        )
        assertTrue(
            "defer must stay below answer",
            defer.top + tolerancePx >= answer.bottom,
        )
        assertTrue(
            "skip must stay below defer",
            skip.top + tolerancePx >= defer.bottom,
        )
        assertTrue(
            "short-height content must not exceed cluster viewport",
            content.bottom <= cluster.bottom + tolerancePx,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun phoneCompact_usesPhoneCluster_notCentered() {
        setInteractiveQuestion()

        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_PHONE_CLUSTER).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.QUESTION_CENTERED_CLUSTER)
                .fetchSemanticsNodes()
                .size,
        )
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_HERO_ORB).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_ANSWER).assertIsDisplayed()
        composeRule.onNodeWithText("Вопрос 3 из 21").assertIsDisplayed()

        val tolerancePx = with(composeRule.density) { 2.dp.toPx() }
        val answer = boundsPx(PracticeTestTags.QUESTION_ANSWER)
        val orb = boundsPx(PracticeTestTags.QUESTION_HERO_ORB)
        assertTrue(
            "phone actions must stay below orb",
            answer.top + tolerancePx >= orb.bottom,
        )
    }

    private fun assertCenteredTabletGeometry() {
        setInteractiveQuestion()

        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_CENTERED_CLUSTER).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.QUESTION_PHONE_CLUSTER)
                .fetchSemanticsNodes()
                .size,
        )
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText("Вопрос 3 из 21").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_HERO_ORB).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_ANSWER).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_DEFER).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_SKIP).assertIsDisplayed()

        val tolerancePx = with(composeRule.density) { 2.dp.toPx() }
        val cluster = boundsPx(PracticeTestTags.QUESTION_CENTERED_CLUSTER)
        val content = boundsPx(PracticeTestTags.QUESTION_CENTERED_CONTENT)
        val orbClipped = boundsPx(PracticeTestTags.QUESTION_HERO_ORB)
        val orbUnclipped = unclippedBoundsPx(PracticeTestTags.QUESTION_HERO_ORB)
        val answer = boundsPx(PracticeTestTags.QUESTION_ANSWER)

        assertTrue(
            "orb must keep full unclipped height (not scroll-clipped)",
            orbClipped.height + tolerancePx >= orbUnclipped.height,
        )
        assertTrue(
            "orb bottom must match unclipped bottom (no horizontal clip edge)",
            orbClipped.bottom + tolerancePx >= orbUnclipped.bottom,
        )
        assertTrue(
            "orb must stay inside centered content viewport",
            orbClipped.top + tolerancePx >= content.top &&
                orbClipped.bottom <= content.bottom + tolerancePx,
        )
        assertTrue(
            "orb must stay inside centered cluster viewport",
            orbClipped.top + tolerancePx >= cluster.top &&
                orbClipped.bottom <= cluster.bottom + tolerancePx,
        )
        assertTrue(
            "content must not be capped near 1/3 of cluster height",
            content.height > cluster.height / 3f + tolerancePx,
        )
        assertTrue(
            "actions must stay below centered cluster",
            answer.top + tolerancePx >= cluster.bottom,
        )
        assertTrue(
            "actions must not overlap orb",
            answer.top + tolerancePx >= orbClipped.bottom,
        )
    }

    private fun setInteractiveQuestion() {
        composeRule.setContent {
            PraktikaTheme {
                QuestionScreen(
                    uiState = QuestionUiState.Interactive(
                        QuestionUiModel(
                            occurrenceId = 1L,
                            questionId = 1,
                            questionTextSnapshot =
                                "Какое ощущение в теле просит внимания прямо сейчас, " +
                                    "без оценки и без спешки?",
                            cyclePosition = 3,
                            status = QuestionOccurrenceStatus.AVAILABLE,
                        ),
                    ),
                    commandState = QuestionCommandState(),
                    onAnswer = {},
                    onDefer = {},
                    onSkip = {},
                    onBackHome = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun boundsPx(tag: String): Rect {
        return composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
    }

    private fun unclippedBoundsPx(tag: String): Rect {
        val dpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Rect(
                left = dpRect.left.toPx(),
                top = dpRect.top.toPx(),
                right = dpRect.right.toPx(),
                bottom = dpRect.bottom.toPx(),
            )
        }
    }
}
// 03.10.2026 Tablet-02 centered cluster fix cursor by Me4Hik END
