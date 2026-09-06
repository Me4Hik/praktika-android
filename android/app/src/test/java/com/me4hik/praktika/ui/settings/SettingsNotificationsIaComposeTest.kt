package com.me4hik.praktika.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.ui.SettingsScreen
import com.me4hik.praktika.ui.theme.PraktikaTheme
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
class SettingsNotificationsIaComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mainSettings_showsNotificationsEntry_andHidesSoundDeferSystemControls() {
        var openedNotifications = false
        composeRule.setContent {
            PraktikaTheme {
                SettingsScreen(
                    uiState = sampleContent(),
                    onSlotTimeChange = { _, _ -> },
                    onOpenNotifications = { openedNotifications = true },
                    onTogglePauseState = {},
                    onBack = {},
                    onStayOnDirtyBack = {},
                    onDiscardChanges = {},
                    showDirtyDialog = false,
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_NOTIFICATIONS_ENTRY)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(openedNotifications)

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DEFER_SECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SOUND_LIBRARY_ENTRY).assertDoesNotExist()
        composeRule.onNodeWithText("Формулировка вопросов").assertIsDisplayed()
    }

    @Test
    fun notificationsScreen_showsSoundDeferAndSystemSettings() {
        var openedSystemSettings = false
        var openedSoundLibrary = false
        composeRule.setContent {
            PraktikaTheme {
                NotificationsSettingsScreen(
                    uiState = sampleContent(),
                    onSoundEnabledChanged = {},
                    onDeferDurationMinutesChanged = {},
                    onOpenNotificationSettings = { openedSystemSettings = true },
                    onOpenSoundLibrary = { openedSoundLibrary = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Уведомления").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SOUND_LIBRARY_ENTRY)
            .assertIsDisplayed()
            .performClick()
        assertTrue(openedSoundLibrary)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DEFER_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DEFER_5).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DEFER_10).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DEFER_15).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DEFER_30).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_NOTIFICATION_SETTINGS)
            .assertIsDisplayed()
            .performClick()
        assertTrue(openedSystemSettings)
        composeRule.onNodeWithText("Системные настройки уведомлений").assertIsDisplayed()
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
