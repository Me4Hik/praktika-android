// 06.09.2026 Archive UX polish cursor by Me4Hik START - compact period dialog host tests
// 06.09.2026 Archive period bounds cursor by Me4Hik START - bounds-aware confirm / picker gating
package com.me4hik.praktika.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
class ArchivePeriodExportDialogComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleBounds = ArchiveAnswerDateBounds(
        earliestEpochDay = 20_000L,
        latestEpochDay = 20_010L,
    )

    @Test
    fun emptyInitial_confirmDisabled_showsUnsetLabels() {
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                    answerDateBounds = sampleBounds,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Начальная дата").assertIsDisplayed()
        composeRule.onNodeWithText("Конечная дата").assertIsDisplayed()
        composeRule.onAllNodesWithText("Не выбрано").assertCountEquals(2)
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun bothDatesPrefill_endAfterStart_confirmEnabledAndInvokes() {
        val start = AtomicLong(-1L)
        val end = AtomicLong(-1L)
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = {},
                    onConfirm = { s, e ->
                        start.set(s)
                        end.set(e)
                    },
                    answerDateBounds = sampleBounds,
                    initialSelectedStartEpochDay = 20_000L,
                    initialSelectedEndEpochDay = 20_005L,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CONFIRM).assertIsEnabled().performClick()
        assertEquals(20_000L, start.get())
        assertEquals(20_005L, end.get())
    }

    @Test
    fun endBeforeStart_confirmDisabled() {
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                    answerDateBounds = sampleBounds,
                    initialSelectedStartEpochDay = 20_010L,
                    initialSelectedEndEpochDay = 20_005L,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun outOfRangeInitial_confirmDisabled() {
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                    answerDateBounds = sampleBounds,
                    initialSelectedStartEpochDay = 19_999L,
                    initialSelectedEndEpochDay = 20_005L,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CONFIRM).assertIsNotEnabled()
        composeRule.onAllNodesWithText("Не выбрано").assertCountEquals(1)
    }

    @Test
    fun emptyArchiveBounds_confirmDisabled_startDoesNotOpenPicker() {
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                    answerDateBounds = null,
                    initialSelectedStartEpochDay = 20_000L,
                    initialSelectedEndEpochDay = 20_005L,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_START).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_PICKER).assertDoesNotExist()
    }

    @Test
    fun cancel_dismissesPeriodDialog() {
        val dismissed = AtomicBoolean(false)
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = { dismissed.set(true) },
                    onConfirm = { _, _ -> },
                    answerDateBounds = sampleBounds,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_CANCEL).performClick()
        assertTrue(dismissed.get())
    }

    @Test
    fun startRow_opensSingleDatePicker_cancelReturnsToPeriodDialog() {
        composeRule.setContent {
            PraktikaTheme {
                ArchivePeriodExportDialog(
                    onDismiss = {},
                    onConfirm = { _, _ -> },
                    answerDateBounds = sampleBounds,
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_START).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_DATE_CANCEL).performClick()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_PICKER).assertDoesNotExist()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_PERIOD_DIALOG).assertIsDisplayed()
    }
}
// 06.09.2026 Archive period bounds cursor by Me4Hik END
// 06.09.2026 Archive UX polish cursor by Me4Hik END
