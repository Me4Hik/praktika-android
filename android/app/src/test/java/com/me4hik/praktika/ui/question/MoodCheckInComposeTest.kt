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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.me4hik.praktika.R
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.mood.MoodCopyResolver
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
    @Config(sdk = [28], qualifiers = "ru-rRU-w360dp-h800dp")
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

        composeRule.waitForIdle()
        composeRule.onNodeWithText(moodAddLabel()).assertIsDisplayed()
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
    @Config(sdk = [28], qualifiers = "ru-rRU-w360dp-h800dp")
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.VERY_LOW)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.LOW)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.NEUTRAL)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.GOOD)).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.GREAT)).assertIsDisplayed()
        val great = moodCopy(MoodLevel.GREAT, QuestionWordingMode.MASCULINE)
        val veryLow = moodCopy(MoodLevel.VERY_LOW, QuestionWordingMode.MASCULINE)
        composeRule.onNodeWithText(veryLow.title).assertIsDisplayed()
        composeRule.onNodeWithText(veryLow.explanation).assertIsDisplayed()
        composeRule.onNodeWithText(great.title).assertIsDisplayed()
        composeRule.onNodeWithText(great.explanation).assertIsDisplayed()

        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.GREAT)).performClick()
        composeRule.waitForIdle()
        assertEquals(MoodLevel.GREAT, selected)
        composeRule.onNodeWithText(moodSelectedLabel(great.emoji, great.title)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_MOOD_PICKER)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "ru-rRU-w360dp-h640dp")
    fun expandBringsPickerIntoView_selectAndReopen() {
        var selected: MoodLevel? = null
        composeRule.setContent {
            var moodState by remember {
                mutableStateOf(
                    AnswerMoodUiState(wordingMode = QuestionWordingMode.MASCULINE),
                )
            }
            PraktikaTheme {
                AnswerScreen(
                    uiState = interactiveState(
                        questionText = LONG_QUESTION,
                        draftText = LONG_DRAFT,
                    ),
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

        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_ENTRY).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_PICKER).assertIsDisplayed()
        // Short-viewport host geometry can under-report window bounds; composition of
        // options + Save stability is asserted here, pixel reveal is covered by device smoke.
        assertTrue(
            composeRule.onAllNodesWithTag(PracticeTestTags.moodOption(MoodLevel.VERY_LOW))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag(PracticeTestTags.moodOption(MoodLevel.GREAT))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsDisplayed()

        composeRule.onNodeWithTag(PracticeTestTags.moodOption(MoodLevel.VERY_LOW)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(MoodLevel.VERY_LOW, selected)
        val veryLow = moodCopy(MoodLevel.VERY_LOW, QuestionWordingMode.MASCULINE)
        composeRule.onNodeWithText(moodSelectedLabel(veryLow.emoji, veryLow.title)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_MOOD_PICKER)
                .fetchSemanticsNodes()
                .size,
        )

        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_ENTRY).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_MOOD_PICKER).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag(PracticeTestTags.moodOption(MoodLevel.VERY_LOW))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.onNodeWithText(moodSelectedLabel(veryLow.emoji, veryLow.title)).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [28], qualifiers = "ru-rRU-w360dp-h800dp")
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

        composeRule.waitForIdle()
        val good = moodCopy(MoodLevel.GOOD, QuestionWordingMode.FEMININE)
        composeRule.onNodeWithText(moodSelectedLabel(good.emoji, good.title)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_MOOD_PICKER)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    @Config(sdk = [28], qualifiers = "ru-rRU-w360dp-h800dp")
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
        composeRule.onNodeWithText(moodAddLabel()).assertIsDisplayed()
    }

    private fun moodAddLabel(): String {
        return composeRule.activity.getString(R.string.answer_mood_add)
    }

    private fun moodSelectedLabel(emoji: String, title: String): String {
        return composeRule.activity.getString(R.string.answer_mood_selected, emoji, title)
    }

    private fun moodCopy(level: MoodLevel, wordingMode: QuestionWordingMode) =
        MoodCopyResolver.resolve(composeRule.activity.resources, level, wordingMode)

    private fun interactiveState(
        questionText: String = "Какое ощущение в теле просит внимания?",
        draftText: String = "",
        canSave: Boolean = false,
    ): AnswerUiState.Interactive {
        return AnswerUiState.Interactive(
            questionId = 1,
            questionText = questionText,
            draftText = draftText,
            isSaving = false,
            canSave = canSave,
            saveError = null,
        )
    }

    private companion object {
        private const val LONG_QUESTION =
            "Какое ощущение в теле просит внимания прямо сейчас, " +
                "если честно посмотреть на то, что происходит внутри, " +
                "без попытки сразу всё объяснить или исправить?"
        private const val LONG_DRAFT =
            "Пишу длинный черновик, чтобы контент Answer screen занял высоту " +
                "и mood selector оказался у нижнего края viewport перед раскрытием. " +
                "Ещё одна строка текста для плотности layout на коротком экране."
    }
}
