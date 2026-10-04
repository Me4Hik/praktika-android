// PROMPT 176 — PDF formatter contract constants (no PdfDocument binary smoke)
package com.me4hik.praktika.export.pdf

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "ru")
class PdfArchiveFormatterContractTest {
    @Test
    fun mimeAndTitleContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("application/pdf", PdfArchiveFormatter.MIME_TYPE)
        assertEquals(
            "Практика — Архив",
            context.getString(R.string.pdf_archive_title),
        )
    }

    @Test
    fun englishTitleViaConfigurationContext() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val en = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        assertEquals(
            "Praktika — Archive",
            en.getString(R.string.pdf_archive_title),
        )
    }
}
