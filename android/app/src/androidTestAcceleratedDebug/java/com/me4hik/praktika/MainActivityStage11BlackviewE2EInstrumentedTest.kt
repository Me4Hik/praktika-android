// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - automated Stage 11 Blackview E2E
package com.me4hik.praktika

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.OnboardingComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.formatTimeOfDayMinutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivityStage11BlackviewE2EInstrumentedTest {
    private val devicePreflightRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
                base.evaluate()
            }
        }
    }

    private val sandboxReset = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                resetAcceleratedSandbox()
                try {
                    base.evaluate()
                } finally {
                    PraktikaRuntimeHolder.resetForTests()
                    MainActivityInitGate.resetForTests()
                }
            }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(devicePreflightRule)
        .around(sandboxReset)
        .around(composeRule)

    @Test
    fun stage11BlackviewAutomatedE2E() {
        runBlocking {
            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()
            PracticeComposeTestSupport.waitUntilOnboarding(composeRule)

            composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_SCREEN).assertIsDisplayed()
            composeRule.onNodeWithText(formatTimeOfDayMinutes(660)).assertIsDisplayed()
            composeRule.onNodeWithText(formatTimeOfDayMinutes(900)).assertIsDisplayed()
            composeRule.onNodeWithText(formatTimeOfDayMinutes(1140)).assertIsDisplayed()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val runtime = PraktikaRuntimeHolder.get(context)

            OnboardingComposeTestSupport.changeSlotTime(composeRule, 1, 630)
            OnboardingComposeTestSupport.changeSlotTime(composeRule, 2, 860)
            OnboardingComposeTestSupport.changeSlotTime(composeRule, 3, 1210)

            composeRule.onNodeWithText(formatTimeOfDayMinutes(630)).assertIsDisplayed()
            composeRule.onNodeWithText(formatTimeOfDayMinutes(860)).assertIsDisplayed()
            composeRule.onNodeWithText(formatTimeOfDayMinutes(1210)).assertIsDisplayed()

            val slotsBeforeStart = runtime.database.scheduleSlotDao().getAllOrderedByTime()
                .associate { it.slotIndex to it.timeOfDayMinutes }
            assertTrue(slotsBeforeStart.values.containsAll(listOf(660, 900, 1140)))

            OnboardingComposeTestSupport.startPractice(composeRule)

            // 03.10.2026 Home question label cursor by Me4Hik START - Scheduled Home after start
            composeRule.waitUntil(timeoutMillis = 20_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_PLANNED_TIME)
            }
            PracticeComposeTestSupport.assertHomePlannedTimeDisplayed(composeRule)
            PracticeComposeTestSupport.assertHomePositionAbsent(composeRule)
            // 03.10.2026 Home question label cursor by Me4Hik END
            composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                .fetchSemanticsNodes()
                .isEmpty()

            Stage11BlackviewE2ESupport.assertCustomSchedulePersisted(runtime, 630, 860, 1210)
            Stage11BlackviewE2ESupport.assertStartedWithCustomFirstOccurrence(runtime, 630)

            try {
                pressBack()
                composeRule.waitForIdle()
                assertTrue(
                    composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                        .fetchSemanticsNodes()
                        .isEmpty(),
                )
            } catch (_: androidx.test.espresso.NoActivityResumedException) {
                // Root Home may finish the activity on Blackview; onboarding must not reappear.
            }

            Stage11BlackviewE2ESupport.assertQuickCheckOk(runtime)
        }
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
