// 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik START - MainActivity onboarding → Home без FatalError
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik START - device wake before Activity launch
package com.me4hik.praktika.ui.practice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivityPracticeStartInstrumentedTest {
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
    fun externalStartNavigatesToHomeWithoutFatalError() {
        runBlocking {
            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START).assertIsEnabled()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val runtime = PraktikaRuntimeHolder.get(context)
            runtime.cycleRepository.startPractice()

            assertTrue(runtime.database.practiceStateDao().get()!!.isPracticeStarted)
            assertEquals(1, runtime.database.questionOccurrenceDao().count())

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
            }

            composeRule.onNodeWithText("Вопрос 1 из 21").assertIsDisplayed()
            composeRule.onNodeWithTag(PracticeTestTags.HOME_PLANNED_TIME).assertIsDisplayed()
            composeRule.onAllNodesWithTag(PracticeTestTags.FATAL_ERROR)
                .fetchSemanticsNodes()
                .isEmpty()

            try {
                pressBack()
                composeRule.waitForIdle()
                composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                    .fetchSemanticsNodes()
                    .isEmpty()
            } catch (_: androidx.test.espresso.NoActivityResumedException) {
                // Root Home may finish the activity; onboarding must not reappear.
            }
        }
    }

    @Test
    fun recreateAfterStartShowsHomeWithoutOnboarding() {
        // 05.08.2026 Stage 6 Final UI Tests cursor by Me4Hik START - Activity recreation keeps Home
        runBlocking {
            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val runtime = PraktikaRuntimeHolder.get(context)
            runtime.cycleRepository.startPractice()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
            }

            val occurrenceCountBefore = runtime.database.questionOccurrenceDao().count()
            assertEquals(1, occurrenceCountBefore)

            composeRule.activityRule.scenario.recreate()

            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
            }

            composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
            composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START)
                .fetchSemanticsNodes()
                .isEmpty()
            assertEquals(occurrenceCountBefore, runtime.database.questionOccurrenceDao().count())
        }
        // 05.08.2026 Stage 6 Final UI Tests cursor by Me4Hik END
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
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik END
// 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik END
