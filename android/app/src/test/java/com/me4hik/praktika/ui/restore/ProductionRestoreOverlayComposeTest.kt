package com.me4hik.praktika.ui.restore

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductionRestoreOverlayComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idle_rendersChooseFolderAndDisclosure() {
        render(ProductionRestoreUiState.Idle)
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_OVERLAY).assertExists()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CHOOSE_FOLDER).assertIsDisplayed()
        composeRule.onNodeWithText("Выберите папку, которую использовали для резервных копий Практики.")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Приложение получит доступ к выбранной папке для восстановления и будущих резервных копий. " +
                "Копия хранится в ней обычным файлом.",
        ).assertIsDisplayed()
    }

    @Test
    fun checkingEligibility_rendersBusyTitle() {
        render(ProductionRestoreUiState.CheckingEligibility)
        composeRule.onNodeWithText("Проверка…").assertIsDisplayed()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_BUSY, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun connecting_rendersBusyCopy() {
        render(ProductionRestoreUiState.Connecting)
        composeRule.onNodeWithText("Подключение…").assertIsDisplayed()
        composeRule.onNodeWithText("Получаем доступ к папке.").assertIsDisplayed()
    }

    @Test
    fun inspecting_rendersBusyCopy() {
        render(ProductionRestoreUiState.Inspecting)
        composeRule.onNodeWithText("Поиск копии…").assertIsDisplayed()
        composeRule.onNodeWithText("Ищем резервную копию в выбранной папке.").assertIsDisplayed()
    }

    @Test
    fun blockedTargetNotEmpty_rendersCloseAction() {
        render(
            ProductionRestoreUiState.Blocked(ProductionRestoreTargetBlockReason.TargetNotEmpty),
        )
        composeRule.onNodeWithText("Восстановление доступно только до начала практики.")
            .assertIsDisplayed()
    }

    @Test
    fun blockedTargetUnsafe_rendersCloseAction() {
        render(
            ProductionRestoreUiState.Blocked(ProductionRestoreTargetBlockReason.TargetUnsafe),
        )
        composeRule.onNodeWithText("Восстановление сейчас недоступно.").assertIsDisplayed()
    }

    @Test
    fun errorNoBackup_rendersChooseOtherFolder() {
        render(ProductionRestoreUiState.Error(ProductionRestoreError.NoBackupFound))
        composeRule.onNodeWithText("В этой папке нет резервной копии Практики.").assertIsDisplayed()
        composeRule.onNodeWithText("Выбрать другую папку").assertIsDisplayed()
    }

    @Test
    fun errorInvalid_rendersCloseOnly() {
        render(ProductionRestoreUiState.Error(ProductionRestoreError.InvalidBackup))
        composeRule.onNodeWithText("Резервная копия повреждена или неполна.").assertIsDisplayed()
    }

    @Test
    fun errorActiveFolderSaveFailure_rendersFolderCopyAndChooseAgain() {
        render(ProductionRestoreUiState.Error(ProductionRestoreError.ActiveFolderSaveFailure))
        composeRule.onNodeWithText("Не удалось подключить папку").assertIsDisplayed()
        composeRule.onNodeWithText("Не удалось сохранить доступ к выбранной папке.").assertIsDisplayed()
        composeRule.onNodeWithText("Выбрать папку снова").assertIsDisplayed()
    }

    @Test
    fun errorRestoreWriteFailure_rendersRestoreWriteCopy() {
        render(ProductionRestoreUiState.Error(ProductionRestoreError.RestoreWriteFailure))
        composeRule.onNodeWithText("Не удалось восстановить данные").assertIsDisplayed()
        composeRule.onNodeWithText("Не удалось сохранить восстановленные данные.").assertIsDisplayed()
        composeRule.onNodeWithText("Закрыть").assertIsDisplayed()
    }

    @Test
    fun previewReady_rendersAllSafeRows() {
        render(
            ProductionRestoreUiState.PreviewReady(
                preview = preview(
                    deletedTextCount = 2,
                ),
            ),
        )
        assertTaggedExists(ProductionRestoreTestTags.RESTORE_PREVIEW_ANSWERS)
        assertTaggedExists(ProductionRestoreTestTags.RESTORE_PREVIEW_COMPLETED)
        assertTaggedExists(ProductionRestoreTestTags.RESTORE_PREVIEW_STATE)
        assertTaggedExists(ProductionRestoreTestTags.RESTORE_PREVIEW_SCHEDULE)
        assertTaggedExists(ProductionRestoreTestTags.RESTORE_PREVIEW_DELETED_TEXT)
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CONFIRM, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Эта папка будет использоваться для будущих резервных копий.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun previewReady_deletedTextHiddenWhenZero() {
        render(
            ProductionRestoreUiState.PreviewReady(
                preview = preview(deletedTextCount = 0),
            ),
        )
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_PREVIEW_DELETED_TEXT)
            .assertDoesNotExist()
    }

    @Test
    fun previewReady_staleNoticeVisible() {
        render(
            ProductionRestoreUiState.PreviewReady(
                preview = preview(),
                staleNotice = true,
            ),
        )
        assertTaggedExists(ProductionRestoreTestTags.RESTORE_STALE_NOTICE)
    }

    @Test
    fun restoring_hasNoCloseOrButtons() {
        render(ProductionRestoreUiState.Restoring(preview = preview()))
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CLOSE).assertDoesNotExist()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithText("Не закрывайте приложение.").assertIsDisplayed()
    }

    @Test
    fun restoreSuccess_requiresContinue() {
        var acknowledged = false
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreOverlay(
                    uiState = ProductionRestoreUiState.RestoreSuccess(preview = preview()),
                    onChooseFolder = {},
                    onRestoreConfirmed = {},
                    onDismiss = {},
                    onSuccessAcknowledged = { acknowledged = true },
                    onRuntimeWarningContinue = {},
                )
            }
        }
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CONTINUE).performClick()
        assertTrue(acknowledged)
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CLOSE).assertDoesNotExist()
    }

    @Test
    fun runtimeWarning_requiresContinueWithoutRetry() {
        var continued = false
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreOverlay(
                    uiState = ProductionRestoreUiState.RuntimeSyncWarning(preview = preview()),
                    onChooseFolder = {},
                    onRestoreConfirmed = {},
                    onDismiss = {},
                    onSuccessAcknowledged = {},
                    onRuntimeWarningContinue = { continued = true },
                )
            }
        }
        composeRule.onNodeWithText(
            "Данные восстановлены, но часть обновления не завершилась. " +
                "Если состояние отображается неверно, закройте и снова откройте приложение.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CONTINUE).performClick()
        assertTrue(continued)
    }

    @Test
    fun actionCallbacks_mapCorrectly() {
        var chooseFolderCalls = 0
        var restoreCalls = 0
        var dismissCalls = 0
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreOverlay(
                    uiState = ProductionRestoreUiState.PreviewReady(preview = preview()),
                    onChooseFolder = { chooseFolderCalls++ },
                    onRestoreConfirmed = { restoreCalls++ },
                    onDismiss = { dismissCalls++ },
                    onSuccessAcknowledged = {},
                    onRuntimeWarningContinue = {},
                )
            }
        }
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CONFIRM).performClick()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CHOOSE_FOLDER).performClick()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CLOSE).performClick()
        assertEquals(1, restoreCalls)
        assertEquals(1, chooseFolderCalls)
        assertEquals(1, dismissCalls)
    }

    @Test
    fun closeIcon_hasAccessibilityDescription() {
        render(ProductionRestoreUiState.Idle)
        composeRule.onNodeWithContentDescription("Закрыть").assertIsDisplayed()
    }

    @Test
    fun preview_doesNotExposeTechnicalFields() {
        render(
            ProductionRestoreUiState.PreviewReady(
                preview = preview(
                    createdAtText = "11 августа 2026 · 18:42",
                ),
            ),
        )
        val forbidden = listOf(
            "checksum",
            "BackupSlotId",
            "backupSequence",
            "content://",
            "answerText",
            "schema",
            "seedVersion",
        )
        forbidden.forEach { token ->
            composeRule.onNodeWithText(token, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun backDismissiblePolicy_blocksTerminalStates() {
        assertFalse(
            ProductionRestoreOverlayPolicy.isBackDismissible(
                ProductionRestoreUiState.Restoring(preview = preview()),
            ),
        )
        assertFalse(
            ProductionRestoreOverlayPolicy.isBackDismissible(
                ProductionRestoreUiState.RestoreSuccess(preview = preview()),
            ),
        )
    }

    private fun assertTaggedExists(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
    }

    private fun render(uiState: ProductionRestoreUiState) {
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreOverlay(
                    uiState = uiState,
                    onChooseFolder = {},
                    onRestoreConfirmed = {},
                    onDismiss = {},
                    onSuccessAcknowledged = {},
                    onRuntimeWarningContinue = {},
                )
            }
        }
    }

    private fun preview(
        deletedTextCount: Int = 0,
        createdAtText: String = "11 августа 2026 · 18:42",
    ): ProductionRestorePreviewUiModel {
        return ProductionRestorePreviewUiModel(
            createdAtText = createdAtText,
            answerCount = 3,
            completedCount = 5,
            practiceStarted = true,
            deletedTextCount = deletedTextCount,
        )
    }
}
