// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik START - repeat history vertical
package com.me4hik.praktika.stage23

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.me4hik.praktika.ui.practice.AcceleratedUiTestHarness
import com.me4hik.praktika.ui.practice.PracticeComposeTestActivity
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelFactory
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage23RepeatHistoryVerticalAcceleratedInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: AcceleratedUiTestHarness
    private val zone = ZoneId.of("Europe/Kiev")

    private val firstAnswerText = """
        Первая строка ответа.
        Вторая строка для IME.
        Третья строка keyboard smoke.
        Четвёртая строка Stage 23.
    """.trimIndent()

    private val secondAnswerText = "Новый независимый ответ cycle 2"

    @Before
    fun assumeOptIn() {
        assumeTrue(
            "Stage 23 runs only via stage23_functional_acceptance.ps1",
            InstrumentationRegistry.getArguments().getString(OPT_IN_ARGUMENT).toBoolean(),
        )
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.tearDown()
        }
    }

    @Test
    fun repeatAnswerHistoryOfferVerticalWithKeyboardSave() {
        setContent(seedRepeatScenario = true)
        PracticeComposeTestSupport.waitForHome(composeRule)

        runBlocking {
            val firstCount = harness.database.answerDao().count()
            assertEquals(1, firstCount)
            harness.timeProvider.advanceToVirtualEpochMillis(epochAt(12, 0, 0))
            harness.timeProvider.checkpoint()
            harness.runtime.cycleRepository.reconcile()
        }

        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        assertTrue(composeRule.onAllNodesWithText("Первый ответ").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput(secondAnswerText)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsEnabled().performClick()
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitForHome(composeRule)

        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        composeRule.onNodeWithText("Посмотреть историю").assertIsDisplayed().performClick()

        ArchiveComposeTestSupport.waitForArchiveQuestionHistoryContent(composeRule)
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, "Первый ответ")
        ArchiveComposeTestSupport.assertQuestionHistoryTextDisplayed(composeRule, secondAnswerText)
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, cycleNumber = 1)
        ArchiveComposeTestSupport.assertQuestionHistoryCycleLabelDisplayed(composeRule, cycleNumber = 2)

        runBlocking {
            assertEquals(2, harness.database.answerDao().count())
            val answers = harness.database.answerDao().getAllOrderedByCreatedAt()
            assertTrue(answers.first().text.contains("Первый"))
            assertEquals(secondAnswerText, answers.last().text)
        }

        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.waitForHomeDisplayed(composeRule)
    }

    private fun setContent(seedRepeatScenario: Boolean) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        harness = AcceleratedUiTestHarness(InstrumentationRegistry.getInstrumentation().targetContext)
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
                        onRequestPostNotifications = {},
                        onOpenNotificationSettings = {},
                    )
                }
                val rootViewModel: PracticeRootViewModel = viewModel(factory = factory)
                AppNavigation(runtime = harness.runtime, viewModel = rootViewModel)
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

    private fun epochAt(hour: Int, minute: Int, second: Int): Long =
        ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    private companion object {
        const val OPT_IN_ARGUMENT = "stage23_functional_acceptance"
    }
}
// 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik END
