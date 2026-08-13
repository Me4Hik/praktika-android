// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - Home permission card connected test
package com.me4hik.praktika.ui.practice

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNotificationCardProductionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: PracticeUiTestHarness

    @Before
    fun setUp() {
        harness = PracticeUiTestHarness(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.tearDown()
        }
    }

    @Test
    fun startedPracticeShowsRequestCardWhenPermissionNotGranted() {
        val requestInvoked = AtomicBoolean(false)
        setContent(
            onRequestPostNotifications = { requestInvoked.set(true) },
        ) {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_STATUS).assertIsDisplayed()
        composeRule.onNodeWithText("Разрешите уведомления, чтобы получать вопросы по расписанию.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_ACTION).performClick()
        composeRule.waitForIdle()
        assertTrue(requestInvoked.get())
    }

    private fun setContent(
        onRequestPostNotifications: () -> Unit = {},
        onOpenAppNotificationSettings: () -> Unit = {},
        onOpenChannelSettings: () -> Unit = {},
        setup: suspend PracticeUiTestHarness.() -> Unit = { setUp() },
    ) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking { harness.setup() }
        composeRule.setContent {
            PraktikaTheme {
                val factory = remember(harness.runtime, composeRule.activity) {
                    PracticeRootViewModelFactory(
                        owner = composeRule.activity,
                        runtime = harness.runtime,
                        onRequestPostNotifications = onRequestPostNotifications,
                        onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                        onOpenChannelSettings = onOpenChannelSettings,
                    )
                }
                val viewModel: PracticeRootViewModel = viewModel(factory = factory)
                AppNavigation(viewModel = viewModel, runtime = harness.runtime)
            }
        }
        composeRule.waitForIdle()
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
