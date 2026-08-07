// 07.08.2026 Stage 21 Share cursor by Me4Hik START - instrumentation ShareIntentFactory tests
package com.me4hik.praktika.share

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentFactoryInstrumentedTest {
    @Test
    fun textShareIntentUsesPlainTextActionSend() {
        val payload = "Question\n\nAnswer"
        val intent = ShareIntentFactory.createTextShareIntent(payload)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(payload, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(intent.`package`)
    }

    @Test
    fun textChooserWrapsShareIntent() {
        val shareIntent = ShareIntentFactory.createTextShareIntent("Payload")
        val chooser = ShareIntentFactory.createChooser(shareIntent, "Share title")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
    }

    @Test
    fun fileShareIntentUsesStreamMimeAndReadGrant() {
        val uri = Uri.parse("content://com.example.fileprovider/share/session/praktika-all.md")
        val intent = ShareIntentFactory.createFileShareIntent(uri, "text/markdown")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/markdown", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
        assertNull(intent.`package`)
        val clipData = intent.clipData
        assertNotNull(clipData)
        assertEquals(1, clipData!!.itemCount)
        assertEquals(uri, clipData.getItemAt(0).uri)
    }

    @Test
    fun fileProviderAuthorityUsesPackageSuffix() {
        assertEquals(
            "com.me4hik.praktika.fileprovider",
            ShareIntentFactory.fileProviderAuthority("com.me4hik.praktika"),
        )
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
