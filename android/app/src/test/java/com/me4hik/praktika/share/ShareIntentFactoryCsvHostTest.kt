// PROMPT 173 — CSV share Intent MIME / no content rewrite
package com.me4hik.praktika.share

import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.export.csv.CsvArchiveFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShareIntentFactoryCsvHostTest {
    @Test
    fun fileShareIntent_csvMimeAndStreamOnly_noTextPayloadRewrite() {
        val uri = Uri.parse(
            "content://com.me4hik.praktika.fileprovider/share/session/praktika-all.csv",
        )
        val intent = ShareIntentFactory.createFileShareIntent(
            contentUri = uri,
            mimeType = CsvArchiveFormatter.MIME_TYPE,
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(CsvArchiveFormatter.MIME_TYPE, intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertNull(intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
    }
}
