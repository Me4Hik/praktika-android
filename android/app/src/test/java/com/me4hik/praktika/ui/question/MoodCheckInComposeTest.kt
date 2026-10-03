package com.me4hik.praktika.ui.question

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.ui.AnswerScreen
import com.me4hik.praktika.ui.practice.AnswerMoodUiState
import com.me4hik.praktika.ui.practice.AnswerUiState
import com.me4hik.praktika.ui.practice.PracticeTestTags
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
class MoodCheckInComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun initialShowsAddMood_andWritingSave_optionsHidden() {
        composeRule.setContent {
            PraktikaTheme {
                AnswerScreen(
                    uiState = interactiveState(),
                    onDraftChanged = {},
                    onSave = {},
                    onBack = {},
                    onBackHome = {},
                    onRetry = {},
                    moodUiState = AnswerMoodUiState(
                        wordingMode = QuestionWordingMode.FEMININE,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Указать настроение").assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_ENTRY).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_MOOD_PICKER)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun expandShowsFiveOptionsWithExplanations_selectCollapses() {
        var selected: MoodLevel? = null
        composeRule.setContent {
            var moodState by remember {
                mutableStateOf(
                    AnswerMoodUiState(wordingMode = QuestionWordingMode.MASCULINE),
                )
            }
            PraktikaTheme {
                AnswerScreen(
                    uiState = interactiveState(),
                    onDraftChanged = {},
                    onSave = {},
                    onBack = {},
                    onBackHome = {},
                    onRetry = {},
                    moodUiState = moodState,
                    onMoodEntryClick = {
                        moodState = moodState.copy(isExpanded = !moodState.isExpanded)
                    },
                    onMoodLevelSelected = { level ->
                        selected = level
                        moodState = moodState.copy(
                            selectedLevel = level,
                            isExpanded = false,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_ENTRY).performClick()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.VERY_LOW)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.LOW)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.NEUTRAL)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.GOOD)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.GREAT)).assertIsDisplayed()
        composeRule.onNodeWithText("Тяжело").assertIsDisplayed()
        composeRule.onNodeWithText("Потерял опору").assertIsDisplayed()
        composeRule.onNodeWithText("Наполнен").assertIsDisplayed()
        composeRule.onNodeWithText("Полон сил и энергии").assertIsDisplayed()

        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.GREAT)).performClick()
        assertEquals(MoodLevel.GREAT, selected)
        composeRule.onNodeWithText("🤩 Наполнен").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_MOOD_PICKER)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun existingMoodOpensCollapsed_notAutoExpanded() {
        composeRule.setContent {
            PraktikaTheme {
                AnswerScreen(
                    uiState = interactiveState(),
                    onDraftChanged = {},
                    onSave = {},
                    onBack = {},
                    onBackHome = {},
                    onRetry = {},
                    moodUiState = AnswerMoodUiState(
                        selectedLevel = MoodLevel.GOOD,
                        wordingMode = QuestionWordingMode.FEMININE,
                        isExpanded = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("🙂 Светло").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_MOOD_PICKER)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "w360dp-h800dp")
    fun answerInputWorksWithoutMood() {
        var draft = ""
        composeRule.setContent {
            var text by remember { mutableStateOf("") }
            PraktikaTheme {
                AnswerScreen(
                    uiState = interactiveState(
                        draftText = text,
                        canSave = text.isNotBlank(),
                    ),
                    onDraftChanged = {
                        text = it
                        draft = it
                    },
                    onSave = {},
                    onBack = {},
                    onBackHome = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("hello")
        assertTrue(draft.contains("hello"))
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsDisplayed()
        composeRule.onNodeWithText("Указать настроение").assertIsDisplayed()
    }

    private fun interactiveState(
        draftText: String = "",
        canSave: Boolean = false,
    ): AnswerUiState.Interactive {
        return AnswerUiState.Interactive(
            questionId = 1,
            questionText = "Какое ощущение в теле просит внимания?",
            draftText = draftText,
            isSaving = false,
            canSave = canSave,
            saveError = null,
        )
    }
}
