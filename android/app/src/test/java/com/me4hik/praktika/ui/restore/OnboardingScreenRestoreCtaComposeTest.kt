package com.me4hik.praktika.ui.restore

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.ui.OnboardingScreen
import com.me4hik.praktika.ui.practice.OnboardingScheduleError
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.practice.PracticeUiError
import com.me4hik.praktika.ui.practice.PracticeUiState
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenRestoreCtaComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun notStarted_normalState_restoreCtaVisible() {
        render(defaultState())
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START).assertIsDisplayed()
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - onboarding primary CTA above schedule
    @Test
    fun startCta_isLaidOutAboveSchedule() {
        render(defaultState())
        val startTop = composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_START)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val restoreTop = composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val scheduleTop = composeRule.onNodeWithTag(PracticeTestTags.ONBOARDING_SCHEDULE)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(startTop < restoreTop)
        assertTrue(restoreTop < scheduleTop)
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    @Test
    fun isStarting_true_restoreCtaDisabled() {
        render(defaultState(isStarting = true))
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).assertIsNotEnabled()
    }

    @Test
    fun isScheduleLoading_true_restoreCtaDisabled() {
        render(defaultState(isScheduleLoading = true))
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).assertIsNotEnabled()
    }

    @Test
    fun scheduleValidationError_restoreCtaEnabled() {
        render(
            defaultState(
                isScheduleValid = false,
                scheduleError = OnboardingScheduleError.DUPLICATE_TIME,
            ),
        )
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).assertIsEnabled()
    }

    @Test
    fun startError_restoreCtaEnabled() {
        render(defaultState(startError = PracticeUiError.START_FAILED))
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).assertIsEnabled()
    }

    @Test
    fun restoreCtaClick_invokesCallbackOnce() {
        var calls = 0
        render(defaultState(), onRestoreBackup = { calls++ })
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).performClick()
        assertEquals(1, calls)
    }

    private fun render(
        state: PracticeUiState.NotStarted,
        onRestoreBackup: () -> Unit = {},
    ) {
        composeRule.setContent {
            PraktikaTheme {
                OnboardingScreen(
                    state = state,
                    onSlotTimeChange = { _, _ -> },
                    onStartPractice = {},
                    onRestoreBackup = onRestoreBackup,
                )
            }
        }
    }

    private fun defaultState(
        isStarting: Boolean = false,
        isScheduleLoading: Boolean = false,
        isScheduleValid: Boolean = true,
        scheduleError: OnboardingScheduleError? = null,
        startError: PracticeUiError? = null,
    ): PracticeUiState.NotStarted {
        return PracticeUiState.NotStarted(
            slots = listOf(
                com.me4hik.praktika.ui.practice.OnboardingScheduleUiModel(1, 660, "11:00"),
                com.me4hik.praktika.ui.practice.OnboardingScheduleUiModel(2, 900, "15:00"),
                com.me4hik.praktika.ui.practice.OnboardingScheduleUiModel(3, 1140, "19:00"),
            ),
            isScheduleLoading = isScheduleLoading,
            isScheduleDirty = false,
            isScheduleValid = isScheduleValid,
            scheduleError = scheduleError,
            isStarting = isStarting,
            startError = startError,
        )
    }
}
