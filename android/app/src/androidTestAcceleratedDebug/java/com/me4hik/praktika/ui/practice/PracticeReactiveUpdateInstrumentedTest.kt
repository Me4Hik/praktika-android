// 05.08.2026 Main Screen cursor by Me4Hik START - accelerated reactive UI test
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik START - старт через repository, не onboarding UI
// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - isolated in-memory runtime
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik START - createAndroidComposeRule restored
package com.me4hik.praktika.ui.practice

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeReactiveUpdateInstrumentedTest {
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
    fun scheduledBecomesAvailableReactivelyWithoutActivityRestart() {
        runBlocking { harness.setUp() }
        setPracticeNavigationContent()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        runBlocking {
            harness.startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)

        runBlocking {
            val occurrenceBefore = harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrenceBefore.status)
            assertEquals(1, harness.database.questionOccurrenceDao().count())

            harness.advanceCurrentOccurrenceToAvailable()

            assertEquals(
                QuestionOccurrenceStatus.AVAILABLE,
                harness.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.status,
            )
        }

        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_QUESTION_TEXT)
        }
        composeRule.onNodeWithTag(PracticeTestTags.HOME_QUESTION_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_ANSWER).assertIsEnabled()
        runBlocking {
            assertEquals(1, harness.database.questionOccurrenceDao().count())
        }
    }

    private fun setPracticeNavigationContent(): PracticeRootViewModel {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
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
}
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik END
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
