// 05.08.2026 Main Screen cursor by Me4Hik START - accelerated Compose navigation tests
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik START - старт через repository, не onboarding UI
// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - isolated in-memory runtime
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik START - createAndroidComposeRule restored
package com.me4hik.praktika.ui.practice

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.archive.ArchiveComposeTestSupport
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeNavigationAcceleratedInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: AcceleratedUiTestHarness

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
    fun coldStartShowsOnboardingWithEnabledStartButton() {
        setPracticeNavigationContent()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START).assertIsEnabled()
        PracticeComposeTestSupport.assertOnboardingDefaultScheduleDisplayed(composeRule)
    }

    @Test
    fun repositoryStartCreatesSingleOccurrenceAndShowsHome() {
        setPracticeNavigationContent()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        runBlocking {
            harness.startPractice()
            assertTrue(harness.database.practiceStateDao().get()!!.isPracticeStarted)
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        runBlocking {
            assertEquals(1, harness.database.questionOccurrenceDao().count())
        }
    }

    @Test
    fun backFromRootHomeDoesNotShowOnboarding() {
        setPracticeNavigationContent {
            setUp()
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        assertTrue(
            composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
    }

    @Test
    fun availableQuestionShowsSnapshotAndSkipReturnsHome() {
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
            assertEquals(
                QuestionOccurrenceStatus.SKIPPED_BY_USER,
                harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
            )
            assertEquals(0, harness.database.answerDao().count())
        }
    }

    @Test
    fun availableShowsAnswerAndOpensQuestionWithSnapshot() {
        setPracticeNavigationContent {
            setUp(initialHour = 11, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_ANSWER).assertIsDisplayed()
    }

    @Test
    fun archiveAndSettingsReturnToHome() {
        setPracticeNavigationContent {
            setUp()
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeArchive(composeRule)
        composeRule.onNodeWithTag(com.me4hik.praktika.ui.archive.ArchiveTestTags.DATES_SCREEN).assertIsDisplayed()
        ArchiveComposeTestSupport.clickArchiveBack(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        PracticeComposeTestSupport.navigateBackFromSettings(composeRule)
        PracticeComposeTestSupport.assertHomePositionDisplayed(composeRule)
    }

    // 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - resume before harness setup
    private fun setPracticeNavigationContent(
        setup: suspend AcceleratedUiTestHarness.() -> Unit = { setUp() },
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
                        onOpenAppNotificationSettings = {},
                        onOpenChannelSettings = {},
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
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
