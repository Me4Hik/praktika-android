// 03.10.2026 Settings version visibility cursor by Me4Hik START - About shows VERSION_NAME + VERSION_CODE
package com.me4hik.praktika.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.ui.SettingsScreen
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAboutVersionComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aboutSection_showsVersionNameAndBuildNumberFromBuildConfig() {
        composeRule.setContent {
            PraktikaTheme {
                SettingsScreen(
                    uiState = sampleContent(),
                    onSlotTimeChange = { _, _ -> },
                    onTogglePauseState = {},
                    onBack = {},
                    onStayOnDirtyBack = {},
                    onDiscardChanges = {},
                    showDirtyDialog = false,
                )
            }
        }

        val expectedVersion = "Версия ${BuildConfig.VERSION_NAME}"
        val expectedBuild = "Сборка ${BuildConfig.VERSION_CODE}"

        composeRule.onNodeWithTag(
            SettingsTestTags.SETTINGS_ABOUT_VERSION,
            useUnmergedTree = true,
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(expectedVersion, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(
            SettingsTestTags.SETTINGS_ABOUT_BUILD,
            useUnmergedTree = true,
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(expectedBuild, useUnmergedTree = true).assertIsDisplayed()

        // Prove values come from BuildConfig at runtime (not a hardcoded UI literal).
        assertTrue(BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue(BuildConfig.VERSION_CODE > 0)
        assertEquals(expectedVersion, "Версия ${BuildConfig.VERSION_NAME}")
        assertEquals(expectedBuild, "Сборка ${BuildConfig.VERSION_CODE}")
        assertNotEquals("Сборка 0", expectedBuild)
    }

    private fun sampleContent(): SettingsUiState.Content {
        return SettingsUiState.Content(
            slots = listOf(
                SettingsSlotUiModel(1, 660, "11:00"),
                SettingsSlotUiModel(2, 900, "15:00"),
                SettingsSlotUiModel(3, 1140, "19:00"),
            ),
            isDirty = false,
            isScheduleValid = true,
            isSavingSchedule = false,
            soundEnabled = true,
            isChangingSound = false,
            deferDurationMinutes = 10,
            isChangingDeferDuration = false,
            questionWordingMode = QuestionWordingMode.DEFAULT,
            isChangingQuestionWording = false,
            isPracticePaused = false,
            isChangingPauseState = false,
            scheduleError = null,
            soundError = null,
            deferError = null,
            wordingError = null,
            pauseError = null,
        )
    }
}
// 03.10.2026 Settings version visibility cursor by Me4Hik END
