// PROMPT 157 — Compose tests for missed occurrences detail screen
package com.me4hik.praktika.ui.analytics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MissedOccurrencesDetailScreenComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun weekdayContent_showsTitleDatetimeAndQuestionText() {
        composeRule.setContent {
            PraktikaTheme {
                MissedOccurrencesDetailScreen(
                    uiState = MissedOccurrencesDetailUiState.Content(
                        mode = MissedOccurrencesDetailMode.Weekday(DayOfWeek.MONDAY),
                        rows = listOf(
                            MissedOccurrenceRowUi(
                                occurrenceId = 7,
                                questionId = 1,
                                questionText = "Inner quiet",
                                formattedDateTime = "8 сентября 2026 · 14:20",
                            ),
                        ),
                    ),
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(MissedOccurrencesDetailTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Пропуски · Понедельник").assertIsDisplayed()
        composeRule.onNodeWithTag("${MissedOccurrencesDetailTestTags.ROW_PREFIX}7").assertIsDisplayed()
        composeRule.onNodeWithText("8 сентября 2026 · 14:20").assertIsDisplayed()
        composeRule.onNodeWithText("Inner quiet").assertIsDisplayed()
    }

    @Test
    fun questionContent_showsHeaderOnceWithoutRowQuestionText() {
        composeRule.setContent {
            PraktikaTheme {
                MissedOccurrencesDetailScreen(
                    uiState = MissedOccurrencesDetailUiState.Content(
                        mode = MissedOccurrencesDetailMode.Question(
                            questionId = 3,
                            questionText = "Header question only",
                        ),
                        rows = listOf(
                            MissedOccurrenceRowUi(
                                occurrenceId = 9,
                                questionId = 3,
                                questionText = null,
                                formattedDateTime = "вторник, 8 сентября 2026 · 14:20",
                            ),
                        ),
                    ),
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Пропуски вопроса").assertIsDisplayed()
        composeRule.onNodeWithTag(MissedOccurrencesDetailTestTags.QUESTION_HEADER).assertIsDisplayed()
        composeRule.onNodeWithText("Header question only").assertIsDisplayed()
        composeRule.onNodeWithText("вторник, 8 сентября 2026 · 14:20").assertIsDisplayed()
    }

    @Test
    fun empty_showsEmptyCopy() {
        composeRule.setContent {
            PraktikaTheme {
                MissedOccurrencesDetailScreen(
                    uiState = MissedOccurrencesDetailUiState.Empty,
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(MissedOccurrencesDetailTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Пропусков пока нет").assertIsDisplayed()
    }

    @Test
    fun error_showsGenericCopyAndRetry() {
        val retries = AtomicInteger(0)
        composeRule.setContent {
            PraktikaTheme {
                MissedOccurrencesDetailScreen(
                    uiState = MissedOccurrencesDetailUiState.Error(message = "technical boom"),
                    onRetry = { retries.incrementAndGet() },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(MissedOccurrencesDetailTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Не удалось загрузить пропуски").assertIsDisplayed()
        composeRule.onNodeWithText("technical boom").assertDoesNotExist()
        composeRule.onNodeWithTag(MissedOccurrencesDetailTestTags.RETRY).performClick()
        assertEquals(1, retries.get())
    }

    @Test
    fun back_invokesCallback() {
        val backed = AtomicBoolean(false)
        composeRule.setContent {
            PraktikaTheme {
                MissedOccurrencesDetailScreen(
                    uiState = MissedOccurrencesDetailUiState.Empty,
                    onRetry = {},
                    onBack = { backed.set(true) },
                )
            }
        }
        composeRule.onNodeWithTag(MissedOccurrencesDetailTestTags.BACK).performClick()
        assertTrue(backed.get())
    }
}
