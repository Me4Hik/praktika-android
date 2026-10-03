// 03.10.2026 Home question label cursor by Me4Hik START - host Compose visibility by MainContent state
package com.me4hik.praktika.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.ui.practice.CurrentOccurrenceUiModel
import com.me4hik.praktika.ui.practice.MainContentUiState
import com.me4hik.praktika.ui.practice.PracticeTestTags
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
class HomeQuestionPositionLabelComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun available_showsQuestionPositionLabel() {
        setHome(MainContentUiState.Available(sampleOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)))
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
        composeRule.onNodeWithText("Вопрос 9 из 21").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_ANSWER).assertIsDisplayed()
    }

    @Test
    fun pausedAvailable_showsQuestionPositionLabel() {
        setHome(MainContentUiState.PausedAvailable(sampleOccurrence(status = QuestionOccurrenceStatus.AVAILABLE)))
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
        composeRule.onNodeWithText("Вопрос 9 из 21").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_QUESTION_TEXT).assertIsDisplayed()
    }

    @Test
    fun scheduled_hidesQuestionPositionLabel_showsNextPlanned() {
        setHome(MainContentUiState.Scheduled(sampleOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)))
        assertEquals(0, composeRule.onAllNodesWithTag(PracticeTestTags.HOME_POSITION).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_PLANNED_TIME).assertIsDisplayed()
        composeRule.onNodeWithText("Следующий вопрос:").assertIsDisplayed()
    }

    @Test
    fun pausedScheduled_hidesQuestionPositionLabel() {
        setHome(MainContentUiState.PausedScheduled(sampleOccurrence(status = QuestionOccurrenceStatus.SCHEDULED)))
        assertEquals(0, composeRule.onAllNodesWithTag(PracticeTestTags.HOME_POSITION).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_PLANNED_TIME).assertIsDisplayed()
        composeRule.onNodeWithText("Следующий вопрос:").assertIsDisplayed()
    }

    private fun setHome(content: MainContentUiState) {
        composeRule.setContent {
            PraktikaTheme {
                HomeScreen(
                    content = content,
                    notificationCard = null,
                    exactAlarmCard = null,
                    onNotificationCardAction = {},
                    onExactAlarmCardAction = {},
                    onOpenQuestion = {},
                    onOpenArchive = {},
                    onOpenSettings = {},
                )
            }
        }
    }

    private fun sampleOccurrence(
        status: QuestionOccurrenceStatus,
    ) = CurrentOccurrenceUiModel(
        occurrenceId = 42L,
        questionId = 9,
        cyclePosition = 9,
        questionText = "Sample active question text",
        status = status,
        plannedAtText = "11:00",
        availableUntilText = "12:00",
    )
}
// 03.10.2026 Home question label cursor by Me4Hik END
