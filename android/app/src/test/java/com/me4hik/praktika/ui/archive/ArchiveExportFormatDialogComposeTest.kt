// PROMPT 180 — format dialog shows Markdown / CSV / PDF
package com.me4hik.praktika.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.export.ExportFormat
import com.me4hik.praktika.ui.theme.PraktikaTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchiveExportFormatDialogComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun formatDialog_showsMarkdownCsvAndPdfOptions() {
        composeRule.setContent {
            PraktikaTheme {
                ArchiveExportFormatDialog(
                    onDismiss = {},
                    onFormatSelected = {},
                )
            }
        }
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_MARKDOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CSV).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_PDF).assertIsDisplayed()
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_CANCEL).assertIsDisplayed()
    }

    @Test
    fun formatDialog_pdfOptionSelectsPdfFormat() {
        val selected = AtomicReference<ExportFormat?>(null)
        composeRule.setContent {
            PraktikaTheme {
                ArchiveExportFormatDialog(
                    onDismiss = {},
                    onFormatSelected = { selected.set(it) },
                )
            }
        }
        assertNull(selected.get())
        composeRule.onNodeWithTag(ArchiveTestTags.EXPORT_FORMAT_PDF).performClick()
        assertEquals(ExportFormat.PDF, selected.get())
    }
}
