package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.ui.SettingsScreen
import com.me4hik.praktika.ui.settings.BackupSettingsUiState
import com.me4hik.praktika.ui.settings.SettingsSlotUiModel
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.settings.SettingsUiState
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TesterUnlockComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unlockGesture_requiresFiveTaps() {
        var count = 0
        repeat(4) {
            val (next, unlocked) = TesterUnlockGesture.onTap(count, alreadyUnlocked = false)
            assertFalse(unlocked)
            count = next
        }
        val (reset, unlocked) = TesterUnlockGesture.onTap(count, alreadyUnlocked = false)
        assertTrue(unlocked)
        assertEquals(0, reset)
    }

    @Test
    fun testerSection_hiddenWhenLocked() {
        composeRule.setContent {
            PraktikaTheme {
                SettingsScreen(
                    uiState = contentState(),
                    onSlotTimeChange = { _, _ -> },
                    onTogglePauseState = {},
                    onBack = {},
                    onStayOnDirtyBack = {},
                    onDiscardChanges = {},
                    showDirtyDialog = false,
                    testerToolsUnlocked = false,
                    onAboutVersionClick = {},
                    onStartInteractiveTour = {},
                )
            }
        }
        assertTrue(
            composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_TESTER_SECTION)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun testerSection_visibleWhenUnlocked() {
        composeRule.setContent {
            PraktikaTheme {
                SettingsScreen(
                    uiState = contentState(),
                    onSlotTimeChange = { _, _ -> },
                    onTogglePauseState = {},
                    onBack = {},
                    onStayOnDirtyBack = {},
                    onDiscardChanges = {},
                    showDirtyDialog = false,
                    testerToolsUnlocked = true,
                    tourResultSummary = "Последнее обучение: завершено",
                    onAboutVersionClick = {},
                    onStartInteractiveTour = {},
                )
            }
        }
        assertTrue(
            composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_TESTER_SECTION)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_START_TOUR)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_TOUR_RESULT)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    private fun contentState() = SettingsUiState.Content(
        slots = listOf(
            SettingsSlotUiModel(1, 8 * 60, "08:00"),
            SettingsSlotUiModel(2, 12 * 60, "12:00"),
            SettingsSlotUiModel(3, 20 * 60, "20:00"),
        ),
        isDirty = false,
        isScheduleValid = true,
        isSavingSchedule = false,
        soundEnabled = true,
        isChangingSound = false,
        deferDurationMinutes = 10,
        isChangingDeferDuration = false,
        questionWordingMode = QuestionWordingMode.NEUTRAL,
        isChangingQuestionWording = false,
        isPracticePaused = false,
        isChangingPauseState = false,
        scheduleError = null,
        soundError = null,
        deferError = null,
        wordingError = null,
        pauseError = null,
        backup = BackupSettingsUiState(
            operationalStatus = BackupSettingsOperationalState.NotConfigured,
        ),
    )
}
