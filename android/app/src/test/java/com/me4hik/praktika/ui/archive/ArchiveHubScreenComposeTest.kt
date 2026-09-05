// PROMPT 167 — Compose tests for Archive hub + by-days list
package com.me4hik.praktika.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchiveHubScreenComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hub_showsActionsWithoutDateList() {
        composeRule.setContent {
            PraktikaTheme {
                ArchiveHubScreen(
                    onOpenDays = {},
                    onOpenQuestions = {},
                    onOpenInsights = {},
                    onExportAll = {},
                    onExportPeriod = {},
                    onShareAll = {},
                    onSharePeriod = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.HUB_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_DAYS).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_QUESTIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_INSIGHTS).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_SCREEN).assertDoesNotExist()
        composeRule.onNodeWithText("Архив по дням").assertIsDisplayed()
    }

    @Test
    fun hub_openDays_invokesCallback() {
        val opened = AtomicBoolean(false)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveHubScreen(
                    onOpenDays = { opened.set(true) },
                    onOpenQuestions = {},
                    onOpenInsights = {},
                    onExportAll = {},
                    onExportPeriod = {},
                    onShareAll = {},
                    onSharePeriod = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_DAYS).performClick()
        assertTrue(opened.get())
    }

    @Test
    fun hub_questionsAndInsights_invokeCallbacks() {
        val opened = AtomicReference<String?>(null)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveHubScreen(
                    onOpenDays = {},
                    onOpenQuestions = { opened.set("questions") },
                    onOpenInsights = { opened.set("insights") },
                    onExportAll = {},
                    onExportPeriod = {},
                    onShareAll = {},
                    onSharePeriod = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_QUESTIONS).performClick()
        assertEquals("questions", opened.get())
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_INSIGHTS).performClick()
        assertEquals("insights", opened.get())
    }

    @Test
    fun daysScreen_showsDatesAndSelectsDay() {
        val selected = AtomicLong(-1L)
        val epochDay = 20_000L
        composeRule.setContent {
            PraktikaTheme {
                ArchiveDatesScreen(
                    uiState = ArchiveDatesUiState.Content(
                        dates = listOf(
                            ArchiveDateListItem(
                                epochDay = epochDay,
                                dateText = "6 сентября 2026",
                                answerCount = 2,
                            ),
                        ),
                    ),
                    onDateSelected = { selected.set(it) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}$epochDay")
            .assertIsDisplayed()
            .performClick()
        assertEquals(epochDay, selected.get())
    }

    @Test
    fun daysScreen_back_invokesCallback() {
        val back = AtomicBoolean(false)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveDatesScreen(
                    uiState = ArchiveDatesUiState.Empty,
                    onDateSelected = {},
                    onBack = { back.set(true) },
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.BACK).performClick()
        assertTrue(back.get())
    }
}
