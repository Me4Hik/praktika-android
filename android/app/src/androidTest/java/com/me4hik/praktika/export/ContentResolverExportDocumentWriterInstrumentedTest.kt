// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - instrumentation writer test
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - byte writer read-back
package com.me4hik.praktika.export

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentResolverExportDocumentWriterInstrumentedTest {
    @Test
    fun writesUtf8MarkdownBytesWithoutBom() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.cacheDir, "markdown-export-writer-test.md")
            if (file.exists()) {
                file.delete()
            }
            val writer = ContentResolverExportDocumentWriter(context.contentResolver)
            val bytes = "Кириллица\nemoji 🎉\nвторая строка".toByteArray(Charsets.UTF_8)
            writer.write(Uri.fromFile(file), bytes)
            assertArrayEquals(bytes, file.readBytes())
            file.delete()
        }
    }

    @Test
    fun writesUtf8CsvBytesWithBomAndMultiline() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.cacheDir, "csv-export-writer-test.csv")
            if (file.exists()) {
                file.delete()
            }
            val writer = ContentResolverExportDocumentWriter(context.contentResolver)
            val body = "answer_id,question_id,question,topic,answer,date,time,cycle_number\r\n" +
                "1,1,\"Многострочный\nответ 🎉\",,=1+1,2026-08-07,12:00,1"
            val bytes = CsvArchiveFormatter.UTF8_BOM + body.toByteArray(Charsets.UTF_8)
            writer.write(Uri.fromFile(file), bytes)
            assertArrayEquals(bytes, file.readBytes())
            file.delete()
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
