// 07.08.2026 Stage 21 Share cursor by Me4Hik START - instrumentation FileProvider temp share
package com.me4hik.praktika.share

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareTempFileStoreInstrumentedTest {
    @Test
    fun fileProviderUriIsContentSchemeAndReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ShareTempFileStore.fromContext(context)
        val markdownBytes = "# Archive\n\nAnswer".toByteArray(Charsets.UTF_8)
        val document = ExportDocument(
            suggestedFileName = "praktika-all.md",
            mimeType = "text/markdown",
            bytes = markdownBytes,
        )
        val file = store.write(document)
        val authority = ShareIntentFactory.fileProviderAuthority(context.packageName)
        val uri = FileProvider.getUriForFile(context, authority, file)
        assertEquals("content", uri.scheme)
        assertTrue(uri.toString().startsWith("content://"))
        context.contentResolver.openInputStream(uri).use { input ->
            assertArrayEquals(markdownBytes, input!!.readBytes())
        }
    }

    @Test
    fun csvBomPreservedThroughProviderRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ShareTempFileStore.fromContext(context)
        val body = "csv-body".toByteArray(Charsets.UTF_8)
        val bytes = CsvArchiveFormatter.UTF8_BOM + body
        val file = store.write(
            ExportDocument(
                suggestedFileName = "praktika-all.csv",
                mimeType = "text/csv",
                bytes = bytes,
            ),
        )
        val authority = ShareIntentFactory.fileProviderAuthority(context.packageName)
        val uri = FileProvider.getUriForFile(context, authority, file)
        context.contentResolver.openInputStream(uri).use { input ->
            assertArrayEquals(bytes, input!!.readBytes())
        }
    }

    @Test
    fun tempFileLivesUnderCacheShareSessionDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ShareTempFileStore.fromContext(context)
        val file = store.write(
            ExportDocument(
                suggestedFileName = "praktika-day-2026-08-07.md",
                mimeType = "text/markdown",
                bytes = byteArrayOf(1, 2, 3),
            ),
        )
        assertTrue(file.absolutePath.contains("${File.separator}cache${File.separator}share${File.separator}"))
        assertEquals("praktika-day-2026-08-07.md", file.name)
        assertTrue(file.length() > 0L)
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
