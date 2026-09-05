// PROMPT 137 — Compose/UI tests for Analytics Insights + Archive entry
package com.me4hik.praktika.ui.analytics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.data.read.analytics.AnalyticsMultiDeferMetrics
import com.me4hik.praktika.ui.archive.ArchiveDatesScreen
import com.me4hik.praktika.ui.archive.ArchiveDatesUiState
import com.me4hik.praktika.ui.archive.ArchiveTestTags
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
class AnalyticsInsightsScreenComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun archiveAction_invokesOnOpenInsights() {
        val opened = AtomicBoolean(false)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveDatesScreen(
                    uiState = ArchiveDatesUiState.Empty,
                    onDateSelected = {},
                    onOpenQuestions = {},
                    onOpenInsights = { opened.set(true) },
                    onExportAll = {},
                    onExportPeriod = {},
                    onShareAll = {},
                    onSharePeriod = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.OPEN_INSIGHTS).assertIsDisplayed().performClick()
        assertTrue(opened.get())
    }

    @Test
    fun loading_showsProgressWithoutContent() {
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = AnalyticsInsightsUiState.Loading,
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun empty_showsEmptyCopy() {
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = AnalyticsInsightsUiState.Empty,
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Пока нет данных для закономерностей").assertIsDisplayed()
    }

    @Test
    fun lowSample_showsBannerAndWeekdayRowsInMonSunOrder() {
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = sampleContent(
                        hasAnyInsightEligible = false,
                        weekdays = listOf(
                            weekdayRow(DayOfWeek.FRIDAY, missed = 1, deferred = 0, rejected = 0, terminals = 2),
                            weekdayRow(DayOfWeek.MONDAY, missed = 0, deferred = 1, rejected = 0, terminals = 2),
                        ),
                    ),
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.LOW_SAMPLE_HINT).assertIsDisplayed()
        composeRule.onNodeWithText("Пока данных немного. Ниже — то, что уже записано.").assertIsDisplayed()
        composeRule.onNodeWithTag("${AnalyticsInsightsTestTags.WEEKDAY_ROW_PREFIX}MONDAY").assertIsDisplayed()
        composeRule.onNodeWithTag("${AnalyticsInsightsTestTags.WEEKDAY_ROW_PREFIX}FRIDAY").assertIsDisplayed()
        composeRule.onNodeWithText("Понедельник").assertIsDisplayed()
        composeRule.onNodeWithText("Пятница").assertIsDisplayed()
        composeRule.onNodeWithText("Отложено 1 из 2").assertIsDisplayed()
        composeRule.onNodeWithText("Пропущено 1 из 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("Отклонено 0 из 2").assertCountEquals(2)
    }

    @Test
    fun content_showsTopMissedDeferredAndEligibleDuration() {
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = sampleContent(
                        hasAnyInsightEligible = true,
                        topMissed = listOf(
                            questionRow(1, "Missed Q", numerator = 4, denominator = 6),
                        ),
                        topDeferred = listOf(
                            questionRow(2, "Deferred Q", numerator = 5, denominator = 7),
                        ),
                        duration = AnalyticsDurationInsight(
                            totalDeferEvents = 8,
                            modeDurationMinutes = 15,
                            averageDurationMinutes = 13.0,
                            histogram = mapOf(15 to 5),
                            eligible = true,
                        ),
                    ),
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.LOW_SAMPLE_HINT).assertDoesNotExist()
        composeRule.onNodeWithTag("${AnalyticsInsightsTestTags.TOP_MISSED_PREFIX}1").assertIsDisplayed()
        composeRule.onNodeWithText("Missed Q").assertIsDisplayed()
        composeRule.onNodeWithText("Пропущено 4 из 6").assertIsDisplayed()
        composeRule.onNodeWithTag("${AnalyticsInsightsTestTags.TOP_DEFERRED_PREFIX}2").assertIsDisplayed()
        composeRule.onNodeWithText("Deferred Q").assertIsDisplayed()
        composeRule.onNodeWithText("Откладывал 5 из 7").assertIsDisplayed()
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.DURATION).assertIsDisplayed()
        composeRule.onNodeWithText("Чаще всего — на 15 мин").assertIsDisplayed()
    }

    @Test
    fun durationIneligible_showsLowSampleCopy() {
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = sampleContent(
                        hasAnyInsightEligible = false,
                        duration = AnalyticsDurationInsight(
                            totalDeferEvents = 3,
                            modeDurationMinutes = 10,
                            averageDurationMinutes = 10.0,
                            histogram = mapOf(10 to 3),
                            eligible = false,
                        ),
                    ),
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Пока 3 записей об отложении").assertIsDisplayed()
    }

    @Test
    fun durationZero_hidesDurationSection() {
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = sampleContent(
                        hasAnyInsightEligible = true,
                        weekdays = listOf(
                            weekdayRow(DayOfWeek.TUESDAY, missed = 1, deferred = 0, rejected = 0, terminals = 1),
                        ),
                        duration = AnalyticsDurationInsight(
                            totalDeferEvents = 0,
                            modeDurationMinutes = null,
                            averageDurationMinutes = null,
                            histogram = emptyMap(),
                            eligible = false,
                        ),
                    ),
                    onRetry = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.DURATION).assertDoesNotExist()
        composeRule.onNodeWithText("Обычно откладываю на…").assertDoesNotExist()
    }

    @Test
    fun error_retryInvokesCallback() {
        val retries = AtomicInteger(0)
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = AnalyticsInsightsUiState.Error(message = "boom"),
                    onRetry = { retries.incrementAndGet() },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.RETRY).performClick()
        assertEquals(1, retries.get())
    }

    @Test
    fun back_invokesOnBack() {
        val backed = AtomicBoolean(false)
        composeRule.setContent {
            PraktikaTheme {
                AnalyticsInsightsScreen(
                    uiState = AnalyticsInsightsUiState.Empty,
                    onRetry = {},
                    onBack = { backed.set(true) },
                )
            }
        }
        composeRule.onNodeWithTag(AnalyticsInsightsTestTags.BACK).performClick()
        assertTrue(backed.get())
    }

    private fun sampleContent(
        hasAnyInsightEligible: Boolean,
        weekdays: List<AnalyticsWeekdayInsightRow> = emptyList(),
        topMissed: List<AnalyticsQuestionInsightRow> = emptyList(),
        topDeferred: List<AnalyticsQuestionInsightRow> = emptyList(),
        duration: AnalyticsDurationInsight = AnalyticsDurationInsight(
            totalDeferEvents = 0,
            modeDurationMinutes = null,
            averageDurationMinutes = null,
            histogram = emptyMap(),
            eligible = false,
        ),
    ) = AnalyticsInsightsUiState.Content(
        weekdays = weekdays,
        topMissed = topMissed,
        topDeferred = topDeferred,
        duration = duration,
        hasAnyInsightEligible = hasAnyInsightEligible,
        multiDefer = AnalyticsMultiDeferMetrics(0, 0),
    )

    private fun weekdayRow(
        day: DayOfWeek,
        missed: Int,
        deferred: Int,
        rejected: Int,
        terminals: Int,
    ) = AnalyticsWeekdayInsightRow(
        dayOfWeek = day,
        terminalCount = terminals,
        missed = AnalyticsOutcomeRate(missed, terminals, missed.toDouble() / terminals, false),
        deferred = AnalyticsOutcomeRate(deferred, terminals, deferred.toDouble() / terminals, false),
        rejected = AnalyticsOutcomeRate(rejected, terminals, rejected.toDouble() / terminals, false),
        deferEventCount = deferred,
    )

    private fun questionRow(
        id: Int,
        text: String,
        numerator: Int,
        denominator: Int,
    ) = AnalyticsQuestionInsightRow(
        questionId = id,
        questionText = text,
        numerator = numerator,
        denominator = denominator,
        rate = numerator.toDouble() / denominator.toDouble(),
        eligible = true,
    )
}
