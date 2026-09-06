// PROMPT 167 — Compose tests for Archive hub + by-days list
// PROMPT 170 — export/share accordion on hub
package com.me4hik.praktika.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_SCREEN).assertDoesNotExist()
        composeRule.onNodeWithText("Архив по дням").assertIsDisplayed()
    }

    @Test
    fun hub_exportShareAccordion_expandsCollapsesAndInvokesCallbacks() {
        val exportAll = AtomicInteger(0)
        val exportPeriod = AtomicInteger(0)
        val shareAll = AtomicInteger(0)
        val sharePeriod = AtomicInteger(0)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveHubScreen(
                    onOpenDays = {},
                    onOpenQuestions = {},
                    onOpenInsights = {},
                    onExportAll = { exportAll.incrementAndGet() },
                    onExportPeriod = { exportPeriod.incrementAndGet() },
                    onShareAll = { shareAll.incrementAndGet() },
                    onSharePeriod = { sharePeriod.incrementAndGet() },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).assertDoesNotExist()

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_DAYS).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_QUESTIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_INSIGHTS).assertIsDisplayed()

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).performClick()
        assertEquals(1, exportAll.get())
        assertEquals(1, exportPeriod.get())
        assertEquals(1, shareAll.get())
        assertEquals(1, sharePeriod.get())

        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_DAYS).assertIsDisplayed()
    }

    // 06.09.2026 Archive period bounds cursor by Me4Hik START - empty archive disables period
    @Test
    fun hub_periodActionsDisabled_whenPeriodSelectionDisabled() {
        val exportPeriod = AtomicInteger(0)
        val sharePeriod = AtomicInteger(0)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveHubScreen(
                    onOpenDays = {},
                    onOpenQuestions = {},
                    onOpenInsights = {},
                    onExportAll = {},
                    onExportPeriod = { exportPeriod.incrementAndGet() },
                    onShareAll = {},
                    onSharePeriod = { sharePeriod.incrementAndGet() },
                    onBack = {},
                    periodSelectionEnabled = false,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_SHARE_ACCORDION).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_ALL).assertIsEnabled()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_ALL).assertIsEnabled()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.SHARE_PERIOD).performClick()
        assertEquals(0, exportPeriod.get())
        assertEquals(0, sharePeriod.get())
    }
    // 06.09.2026 Archive period bounds cursor by Me4Hik END

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

    // 06.09.2026 Archive UX polish cursor by Me4Hik START - Ответов: 1 visible on by-days list
    @Test
    fun daysScreen_answerCountOne_showsSupportingLabel() {
        val epochDay = 20_001L
        composeRule.setContent {
            PraktikaTheme {
                ArchiveDatesScreen(
                    uiState = ArchiveDatesUiState.Content(
                        dates = listOf(
                            ArchiveDateListItem(
                                epochDay = epochDay,
                                dateText = "5 сентября 2026",
                                answerCount = 1,
                            ),
                        ),
                    ),
                    onDateSelected = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("${ArchiveTestTags.DATE_ITEM_PREFIX}$epochDay").assertIsDisplayed()
        composeRule.onNodeWithText("Ответов: 1").assertIsDisplayed()
    }
    // 06.09.2026 Archive UX polish cursor by Me4Hik END

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
