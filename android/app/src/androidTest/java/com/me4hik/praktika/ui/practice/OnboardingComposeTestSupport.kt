// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - shared onboarding Compose test helpers
package com.me4hik.praktika.ui.practice

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.ui.settings.SettingsComposeTestSupport

object OnboardingComposeTestSupport {
    fun changeSlotTime(
        composeRule: ComposeContentTestRule,
        slotIndex: Int,
        timeOfDayMinutes: Int,
    ) {
        SettingsComposeTestSupport.changeSlotTimeWithTags(
            composeRule = composeRule,
            slotTestTag = onboardingSlotTestTag(slotIndex),
            pickerTestTag = PracticeTestTags.ONBOARDING_TIME_PICKER,
            timeOfDayMinutes = timeOfDayMinutes,
        )
    }

    fun startPractice(composeRule: ComposeContentTestRule) {
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START).performClick()
        composeRule.waitForIdle()
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
