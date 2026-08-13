package com.me4hik.praktika.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.ui.theme.PraktikaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsBackupSectionComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun notConfigured_showsSetupCta() {
        composeRule.setContent {
            PraktikaTheme {
                SettingsBackupSection(
                    backup = BackupSettingsUiState(
                        operationalStatus = BackupSettingsOperationalState.NotConfigured,
                    ),
                    onSetup = {},
                    onBackupNow = {},
                    onChangeFolder = {},
                    onReconnect = {},
                    onDisable = {},
                )
            }
        }
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACKUP_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Папка для резервных копий не выбрана.").assertIsDisplayed()
        composeRule.onNodeWithText("Настроить резервную копию").assertIsDisplayed()
    }

    @Test
    fun healthy_showsBackupNowAndSecondaryActions() {
        composeRule.setContent {
            PraktikaTheme {
                SettingsBackupSection(
                    backup = BackupSettingsUiState(
                        operationalStatus = BackupSettingsOperationalState.Healthy(1_700_000_000_000L),
                    ),
                    onSetup = {},
                    onBackupNow = {},
                    onChangeFolder = {},
                    onReconnect = {},
                    onDisable = {},
                )
            }
        }
        composeRule.onNodeWithText("Резервное копирование включено").assertIsDisplayed()
        composeRule.onNodeWithText("Создать копию сейчас").assertIsDisplayed()
        composeRule.onNodeWithText("Изменить папку").assertIsDisplayed()
        composeRule.onNodeWithText("Отключить резервное копирование").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACKUP_LAST_SUCCESS).assertIsDisplayed()
    }

    @Test
    fun needsReconnect_showsReconnectPrimary() {
        composeRule.setContent {
            PraktikaTheme {
                SettingsBackupSection(
                    backup = BackupSettingsUiState(
                        operationalStatus = BackupSettingsOperationalState.NeedsReconnect(null),
                    ),
                    onSetup = {},
                    onBackupNow = {},
                    onChangeFolder = {},
                    onReconnect = {},
                    onDisable = {},
                )
            }
        }
        composeRule.onNodeWithText("Нет доступа к папке резервных копий.").assertIsDisplayed()
        composeRule.onNodeWithText("Восстановить доступ").assertIsDisplayed()
    }
}
