// 05.08.2026 Stage 6 Boundary cursor by Me4Hik START - production Compose navigation tests
// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - isolated in-memory runtime
// 05.08.2026 Question And Skip cursor by Me4Hik START - Question/Answer/skip navigation
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik START - Stage 6 Activity harness restored
package com.me4hik.praktika.ui.practice

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.archive.ArchiveComposeTestSupport
import com.me4hik.praktika.ui.archive.ArchiveTestTags
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeNavigationProductionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: PracticeUiTestHarness

    @Before
    fun setUp() {
        harness = PracticeUiTestHarness(
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
    fun notStartedShowsOnboardingWithEnabledStartButton() {
        setPracticeNavigationContent()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START).assertIsEnabled()
        PracticeComposeTestSupport.assertOnboardingDefaultScheduleDisplayed(composeRule)
    }

    @Test
    fun preparedStartedStateShowsHomeScheduledWithoutSnapshot() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_PLANNED_TIME).assertIsDisplayed()
        assertFalse(
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_QUESTION_TEXT),
        )
    }

    @Test
    fun externalStartPracticeNavigatesToHome() {
        setPracticeNavigationContent()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        runBlocking {
            harness.startPractice()
            assertTrue(harness.database.practiceStateDao().get()!!.isPracticeStarted)
            assertEquals(1, harness.database.questionOccurrenceDao().count())
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
    }

    @Test
    fun repositoryStartCreatesSingleOccurrenceAndShowsHome() {
        setPracticeNavigationContent()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        runBlocking {
            harness.startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        runBlocking {
            assertEquals(1, harness.database.questionOccurrenceDao().count())
        }
    }

    @Test
    fun availableShowsSnapshotAndAnswerButton() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        runBlocking {
            val status = harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, status)
        }
        composeRule.onNodeWithTag(PracticeTestTags.HOME_QUESTION_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_ANSWER).assertIsDisplayed()
    }

    @Test
    fun availableQuestionShowsSnapshotAndActionButtons() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_ANSWER).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_SKIP).assertIsDisplayed()
    }

    @Test
    fun answerSaveReturnsHomeWithConfirmation() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("Да")
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).performClick()
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
        runBlocking {
            val first = harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            assertEquals(QuestionOccurrenceStatus.ANSWERED, first.status)
            assertEquals(1, harness.database.answerDao().count())
            assertEquals("Да", harness.database.answerDao().getByOccurrenceId(first.id)!!.text)
            assertEquals(2, harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!.cyclePosition)
        }
        try {
            pressBack()
            composeRule.waitForIdle()
            assertFalse(
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT),
            )
        } catch (_: androidx.test.espresso.NoActivityResumedException) {
        }
    }

    @Test
    fun answerBlankSaveDisabledAndMultilineInput() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsNotEnabled()
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput("Line1\nLine2")
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsDisplayed()
    }

    @Test
    fun answerBackReturnsToQuestion() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_BACK).performClick()
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_TEXT).assertIsDisplayed()
    }

    @Test
    fun skipReturnsHomeAndDoesNotReopenSkippedQuestion() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        PracticeComposeTestSupport.clickQuestionSkip(composeRule)
        composeRule.waitForIdle()
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)
        runBlocking {
            val first = harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, first.status)
            assertEquals(0, harness.database.answerDao().count())
            val second = harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
            assertEquals(QuestionOccurrenceStatus.SCHEDULED, second.status)
        }
        try {
            pressBack()
            composeRule.waitForIdle()
            assertFalse(
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.QUESTION_TEXT),
            )
        } catch (_: androidx.test.espresso.NoActivityResumedException) {
            // Root Home may finish the activity; skipped question must not reappear.
        }
    }

    @Test
    fun scheduledDoesNotExposeQuestionSnapshotOnHome() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        assertFalse(
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER),
        )
    }

    @Test
    fun archiveAndSettingsReturnToHome() {
        setPracticeNavigationContent {
            setUp()
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeArchive(composeRule)
        composeRule.onNodeWithTag(ArchiveTestTags.DATES_SCREEN).assertIsDisplayed()
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        PracticeComposeTestSupport.navigateBackFromSettings(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)
    }

    @Test
    fun isolatedHarnessDoesNotUseProductionDatabaseFile() {
        setPracticeNavigationContent()
        assertFalse(harness.usesProductionDatabaseFile)
        val productionDb = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath(PracticeUiTestHarness.PRODUCTION_DATABASE_NAME)
        val databasePath = checkNotNull(harness.database.openHelper.writableDatabase.path)
        assertFalse(databasePath.contains(PracticeUiTestHarness.PRODUCTION_DATABASE_NAME))
        assertEquals(
            PracticeUiTestHarness.IN_MEMORY_DATABASE_LABEL,
            harness.runtime.databaseName,
        )
        productionDb.parentFile?.mkdirs()
    }

    @Test
    fun testDatabaseClosesAfterHarnessTearDown() {
        setPracticeNavigationContent()
        assertTrue(harness.database.isOpen)
        harness.tearDown()
        assertFalse(harness.database.isOpen)
    }

    // 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - resume before harness setup
    private fun setPracticeNavigationContent(
        setup: suspend PracticeUiTestHarness.() -> Unit = { setUp() },
    ): PracticeRootViewModel {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking { harness.setup() }
        val holder = arrayOfNulls<PracticeRootViewModel>(1)
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
                val viewModel: PracticeRootViewModel = viewModel(factory = factory)
                holder[0] = viewModel
                AppNavigation(viewModel = viewModel, runtime = harness.runtime)
            }
        }
        composeRule.waitForIdle()
        runBlocking {
            PracticeComposeTestHarness.awaitInitialUiState(checkNotNull(holder[0]))
        }
        composeRule.waitForIdle()
        return checkNotNull(holder[0])
    }
    // 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
}
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik END
// 05.08.2026 Question And Skip cursor by Me4Hik END
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik END
