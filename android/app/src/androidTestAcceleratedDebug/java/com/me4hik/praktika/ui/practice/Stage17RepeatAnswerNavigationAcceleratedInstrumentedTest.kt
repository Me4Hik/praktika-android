// 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - repeat answer navigation tests
package com.me4hik.praktika.ui.practice

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.archive.ArchiveComposeTestSupport
import com.me4hik.praktika.ui.archive.ArchiveTestTags
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage17RepeatAnswerNavigationAcceleratedInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: AcceleratedUiTestHarness
    private val zone = ZoneId.of("Europe/Kiev")

    @Before
    fun setUp() {
        harness = AcceleratedUiTestHarness(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.tearDown()
        }
    }

    @Test
    fun firstAnswerSaveShowsConfirmationWithoutHistoryAction() {
        setContent()
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("Первый ответ")
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).performClick()
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Посмотреть историю").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun repeatAnswerOffersHistoryAndOpensBothAnswers() {
        setContent(seedRepeatScenario = true)
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        assertTrue(composeRule.onAllNodesWithText("Первый ответ").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("Новый независимый ответ")
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).performClick()
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        composeRule.onNodeWithText("Посмотреть историю").assertIsDisplayed().performClick()
        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Первый ответ")
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Новый независимый ответ")
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, cycleNumber = 1)
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, cycleNumber = 2)
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomeStartedContentDisplayed(composeRule)
        assertTrue(composeRule.onAllNodesWithTag(PracticeTestTags.ANSWER_INPUT).fetchSemanticsNodes().isEmpty())
    }

    private fun setContent(seedRepeatScenario: Boolean = false) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking {
            harness.setUp(initialHour = 11, initialMinute = 0)
            if (seedRepeatScenario) {
                seedRepeatQuestionScenario()
            } else {
                harness.startPractice()
            }
        }
        composeRule.setContent {
            PraktikaTheme {
                val factory = remember(harness.runtime, composeRule.activity) {
                    PracticeRootViewModelFactory(
                        owner = composeRule.activity,
                        runtime = harness.runtime,
                    )
                }
                val rootViewModel: PracticeRootViewModel = viewModel(factory = factory)
                AppNavigation(
                    runtime = harness.runtime,
                    viewModel = rootViewModel,
                )
            }
        }
    }

    private suspend fun seedRepeatQuestionScenario() {
        val plannedAt = epochAt(11, 0, 0)
        val availableUntil = epochAt(15, 0, 0)
        val completedAt = epochAt(11, 30, 0)
        val cycleOneOccurrenceId = harness.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Repeat question cycle 1",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = completedAt,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = zone.id,
            ),
        )
        harness.database.answerDao().insert(
            AnswerEntity(
                occurrenceId = cycleOneOccurrenceId,
                text = "Первый ответ",
                createdAtEpochMillis = completedAt,
            ),
        )
        harness.database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = 1,
                questionTextSnapshot = "Repeat question cycle 2",
                cycleNumber = 2,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.AVAILABLE,
                zoneId = zone.id,
            ),
        )
        harness.database.practiceStateDao().update(
            harness.database.practiceStateDao().get()!!.copy(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = plannedAt,
                currentCycleNumber = 2,
                nextCyclePosition = 2,
                lastProcessedAtEpochMillis = plannedAt,
                activeZoneId = zone.id,
            ),
        )
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, zone)
            .toInstant()
            .toEpochMilli()
    }
}
// 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
