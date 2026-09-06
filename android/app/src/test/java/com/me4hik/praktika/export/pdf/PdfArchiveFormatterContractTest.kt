// PROMPT 176 — PDF formatter contract constants (no PdfDocument binary smoke)
package com.me4hik.praktika.export.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfArchiveFormatterContractTest {
    @Test
    fun mimeAndTitleContract() {
        assertEquals("application/pdf", PdfArchiveFormatter.MIME_TYPE)
        assertEquals("Практика — Архив", PdfArchiveFormatter.TITLE)
    }
}
