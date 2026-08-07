// 07.08.2026 Stage 21 Share cursor by Me4Hik START - unit tests ShareTempFileStore
package com.me4hik.praktika.share

import com.me4hik.praktika.export.ExportDocument
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShareTempFileStoreTest {
    private lateinit var shareRoot: File
    private var currentTimeMillis = System.currentTimeMillis()
    private lateinit var store: ShareTempFileStore

    @Before
    fun setUp() {
        shareRoot = File.createTempFile("share-temp-store-test", null).apply {
            delete()
            mkdir()
        }
        store = ShareTempFileStore(
            shareRoot = shareRoot,
            clockMillis = { currentTimeMillis },
        )
    }

    @Test
    fun writeCreatesFileUnderShareRootWithSuggestedFileName() {
        val document = ExportDocument(
            suggestedFileName = "praktika-all.md",
            mimeType = "text/markdown",
            bytes = "# Archive".toByteArray(Charsets.UTF_8),
        )
        val file = store.write(document)
        assertTrue(file.absolutePath.startsWith(shareRoot.absolutePath))
        assertEquals("praktika-all.md", file.name)
        assertArrayEquals(document.bytes, file.readBytes())
    }

    @Test
    fun markdownBytesWrittenExact() {
        val bytes = "# Title\n\nAnswer".toByteArray(Charsets.UTF_8)
        val file = store.write(
            ExportDocument(
                suggestedFileName = "praktika-day-2026-08-07.md",
                mimeType = "text/markdown",
                bytes = bytes,
            ),
        )
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun csvBomPreserved() {
        val body = "header".toByteArray(Charsets.UTF_8)
        val bytes = CsvArchiveFormatter.UTF8_BOM + body
        val file = store.write(
            ExportDocument(
                suggestedFileName = "praktika-all.csv",
                mimeType = "text/csv",
                bytes = bytes,
            ),
        )
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun sessionDirectoriesDoNotConflict() {
        val document = ExportDocument(
            suggestedFileName = "praktika-all.md",
            mimeType = "text/markdown",
            bytes = byteArrayOf(1),
        )
        val first = store.write(document)
        val second = store.write(document)
        assertTrue(first.parentFile!!.absolutePath != second.parentFile!!.absolutePath)
    }

    @Test
    fun cleanupRemovesOnlyOldSessionDirectories() {
        val oldSession = File(shareRoot, "old-session")
        check(oldSession.mkdirs())
        File(oldSession, "praktika-all.md").writeBytes(byteArrayOf(9))

        val freshSession = File(shareRoot, "fresh-session")
        check(freshSession.mkdirs())
        val freshFile = File(freshSession, "praktika-all.md")
        freshFile.writeBytes(byteArrayOf(7))

        check(oldSession.setLastModified(currentTimeMillis - 25L * 60L * 60L * 1000L))
        check(freshSession.setLastModified(currentTimeMillis - 1L))

        store.cleanupOldSessions()

        assertTrue(!oldSession.exists())
        assertTrue(freshSession.exists())
        assertArrayEquals(byteArrayOf(7), freshFile.readBytes())
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
