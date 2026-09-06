// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik START - XLSX share Intent MIME / stream
package com.me4hik.praktika.share

import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.export.xlsx.XlsxArchiveFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShareIntentFactoryXlsxHostTest {
    @Test
    fun fileShareIntent_xlsxMimeAndStreamOnly_noTextPayloadRewrite() {
        val uri = Uri.parse(
            "content://com.me4hik.praktika.fileprovider/share/session/praktika-all.xlsx",
        )
        val intent = ShareIntentFactory.createFileShareIntent(
            contentUri = uri,
            mimeType = XlsxArchiveFormatter.MIME_TYPE,
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(XlsxArchiveFormatter.MIME_TYPE, intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertNull(intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
    }
}
// 06.09.2026 Android Sheets XLSX Export cursor by Me4Hik END
