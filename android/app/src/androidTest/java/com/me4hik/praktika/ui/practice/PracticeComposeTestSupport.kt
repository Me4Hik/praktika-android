// 05.08.2026 Main Navigation Fix cursor by Me4Hik START - shared Compose UI test helpers
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik START - resume helper и Stage 6 waits
package com.me4hik.praktika.ui.practice

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.ui.settings.SettingsTestTags

object PracticeComposeTestSupport {
    fun waitUntilOnboarding(composeRule: ComposeContentTestRule) {
        waitUntilNotLoading(composeRule)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }
        // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - wait for loaded schedule rows
        composeRule.waitUntil(timeoutMillis = 10_000) {
            hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_SLOT_1)
        }
        // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
    }

    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - scroll-safe onboarding schedule assertions
    fun assertOnboardingDefaultScheduleDisplayed(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_DESCRIPTION)
            .performScrollTo()
            .assertIsDisplayed()
        listOf("11:00", "15:00", "19:00").forEach { time ->
            composeRule.onNodeWithText(time)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
    // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END

    fun waitUntilNotLoading(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                if (!hasComposeHierarchy(composeRule)) {
                    return@waitUntil false
                }
                val loading = composeRule.onAllNodesWithTag(PracticeTestTags.LOADING).fetchSemanticsNodes()
                val onboarding = composeRule.onAllNodesWithTag(PracticeTestTags.ONBOARDING_START).fetchSemanticsNodes()
                // 03.10.2026 Home question label cursor by Me4Hik START - Scheduled Home has no HOME_POSITION
                val homePosition = composeRule.onAllNodesWithTag(PracticeTestTags.HOME_POSITION).fetchSemanticsNodes()
                val homePlanned = composeRule.onAllNodesWithTag(PracticeTestTags.HOME_PLANNED_TIME).fetchSemanticsNodes()
                val home = homePosition.isNotEmpty() || homePlanned.isNotEmpty()
                // 03.10.2026 Home question label cursor by Me4Hik END
                loading.isEmpty() && (onboarding.isNotEmpty() || home)
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    fun waitForHome(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION) ||
                hasNodeWithTag(composeRule, PracticeTestTags.HOME_PLANNED_TIME) ||
                hasNodeWithTag(composeRule, PracticeTestTags.HOME_QUESTION_TEXT) ||
                hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        }
    }

    // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - scroll + async navigation waits
    // 03.10.2026 Home question label cursor by Me4Hik START - assert by Available vs Scheduled semantics
    fun waitForHomeDisplayed(
        composeRule: ComposeContentTestRule,
    ) {
        waitForHome(composeRule)
        assertHomeStartedContentDisplayed(composeRule)
    }

    /**
     * Confirms Started Home by concrete state semantics:
     * - Available / PausedAvailable → [HOME_POSITION] present
     * - Scheduled / PausedScheduled → [HOME_PLANNED_TIME] present and [HOME_POSITION] absent
     */
    fun assertHomeStartedContentDisplayed(
        composeRule: ComposeContentTestRule,
    ) {
        val hasPlanned = hasNodeWithTag(composeRule, PracticeTestTags.HOME_PLANNED_TIME)
        val hasPosition = hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
        val hasActiveQuestion =
            hasNodeWithTag(composeRule, PracticeTestTags.HOME_QUESTION_TEXT) ||
                hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        when {
            hasPlanned -> {
                assertHomePlannedTimeDisplayed(composeRule)
                assertHomePositionAbsent(composeRule)
            }
            hasActiveQuestion || hasPosition -> {
                assertHomePositionDisplayed(composeRule)
            }
            else -> {
                throw AssertionError(
                    "Home started content not recognized: expected planned-time or active question tags",
                )
            }
        }
    }

    fun assertHomePositionAbsent(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onAllNodesWithTag(PracticeTestTags.HOME_POSITION).assertCountEquals(0)
    }
    // 03.10.2026 Home question label cursor by Me4Hik END

    fun waitForQuestion(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag(PracticeTestTags.QUESTION_TEXT).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    fun clickHomeAnswer(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(PracticeTestTags.HOME_ANSWER)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        waitForQuestion(composeRule)
    }

    fun clickQuestionAnswer(
        composeRule: ComposeContentTestRule,
    ) {
        // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - pinned question actions
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_ANSWER)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
    }

    fun clickQuestionSkip(
        composeRule: ComposeContentTestRule,
    ) {
        // 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - pinned question actions
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_SKIP)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        // 07.08.2026 Stage 24 Final Design cursor by Me4Hik END
    }

    fun clickHomeArchive(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(PracticeTestTags.HOME_ARCHIVE)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
    }

    fun clickHomeSettings(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(PracticeTestTags.HOME_SETTINGS)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
    }

    fun navigateBackFromSettings(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        pressBack()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_SCREEN)
        }
        waitForHomeDisplayed(composeRule)
    }

    // 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik START - Settings back at scroll bottom
    fun clickSettingsBackToHome(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACK)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        if (hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_DIRTY_DIALOG)) {
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DISCARD).performClick()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_SCREEN)
        }
        waitForHomeDisplayed(composeRule)
    }
    // 07.08.2026 Stage 23 Functional Acceptance cursor by Me4Hik END

    fun navigateBackWithText(
        composeRule: ComposeContentTestRule,
        text: String = "Назад",
    ) {
        composeRule.onNodeWithText(text)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitForHomeDisplayed(composeRule)
    }

    fun assertHomePlannedTimeDisplayed(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(PracticeTestTags.HOME_PLANNED_TIME)
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun assertHomePositionDisplayed(
        composeRule: ComposeContentTestRule,
    ) {
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION)
            .performScrollTo()
            .assertIsDisplayed()
    }
    // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END

    fun hasNodeWithTag(
        composeRule: ComposeContentTestRule,
        tag: String,
    ): Boolean {
        return try {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }

    // 03.10.2026 Home question label cursor by Me4Hik START - Home ready without requiring HOME_POSITION
    fun hasHomeStartedContent(composeRule: ComposeContentTestRule): Boolean {
        return hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION) ||
            hasNodeWithTag(composeRule, PracticeTestTags.HOME_PLANNED_TIME) ||
            hasNodeWithTag(composeRule, PracticeTestTags.HOME_QUESTION_TEXT) ||
            hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
    }
    // 03.10.2026 Home question label cursor by Me4Hik END

    fun hasNodeWithText(
        composeRule: ComposeContentTestRule,
        text: String,
    ): Boolean {
        return try {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun <A : ComponentActivity> ensureTestActivityResumed(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<A>, A>,
    ) {
        wakeTestDeviceIfNeeded()
        keepScreenOnDuringTest()
        val scenario = composeRule.activityRule.scenario
        val currentState = scenario.state
        if (currentState == Lifecycle.State.DESTROYED) {
            throw IllegalStateException(
                "Test Activity is DESTROYED before setContent. Run Blackview wake/unlock preflight.",
            )
        }
        if (currentState != Lifecycle.State.RESUMED) {
            resumeTestActivity(scenario, composeRule.activity.javaClass as Class<A>)
            composeRule.waitForIdle()
        }
        val resumedState = scenario.state
        if (resumedState != Lifecycle.State.RESUMED) {
            throw IllegalStateException(
                "Test Activity did not stay RESUMED (state=$resumedState). " +
                    "Device may be Dozing or launcher holds focus.",
            )
        }
    }

    private fun <A : ComponentActivity> resumeTestActivity(
        scenario: androidx.test.core.app.ActivityScenario<A>,
        activityClass: Class<A>,
    ) {
        try {
            scenario.moveToState(Lifecycle.State.RESUMED)
            if (scenario.state == Lifecycle.State.RESUMED) {
                return
            }
        } catch (_: AssertionError) {
        }
        bringActivityToFront(activityClass)
        try {
            scenario.recreate()
        } catch (_: AssertionError) {
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun <A : ComponentActivity> bringActivityToFront(activityClass: Class<A>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "${instrumentation.targetContext.packageName}/${activityClass.name}"
        instrumentation.uiAutomation
            .executeShellCommand("am start -n $component -f 0x24000000")
            .close()
        Thread.sleep(300)
    }

    fun keepScreenOnDuringTest() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("svc power stayon true").close()
    }
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END

    fun wakeTestDeviceIfNeeded() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation
        uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        uiAutomation.executeShellCommand("input keyevent 82").close()
    }

    private fun hasComposeHierarchy(
        composeRule: ComposeContentTestRule,
    ): Boolean {
        return try {
            composeRule.onAllNodesWithTag(PracticeTestTags.LOADING, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
            true
        } catch (_: IllegalStateException) {
            false
        }
    }
}
// 05.08.2026 Stage 7 Connected Harness Fix cursor by Me4Hik END
// 05.08.2026 Main Navigation Fix cursor by Me4Hik END
