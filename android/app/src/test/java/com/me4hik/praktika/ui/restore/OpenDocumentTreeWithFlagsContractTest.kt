package com.me4hik.praktika.ui.restore

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenDocumentTreeWithFlagsContractTest {
    private val contract = OpenDocumentTreeWithFlagsContract()
    private val context = org.robolectric.RuntimeEnvironment.getApplication()

    @Test
    fun createIntent_usesOpenDocumentTreeWithReadWritePersistableFlags() {
        val intent = contract.createIntent(context, null)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue(
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        assertTrue(
            intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
        )
        assertTrue(
            intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0,
        )
    }

    @Test
    fun createIntent_withInitialUri_setsExtraInitialUri() {
        val initialUri = Uri.parse("content://com.test/tree/initial")
        val intent = contract.createIntent(context, initialUri)

        assertEquals(initialUri, intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    @Test
    fun parseResult_resultOk_returnsUriAndFlags() {
        val uri = Uri.parse("content://com.test/tree/selected")
        val resultIntent = Intent().apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        val parsed = contract.parseResult(Activity.RESULT_OK, resultIntent)

        assertEquals(uri, parsed?.uri)
        assertEquals(resultIntent.flags, parsed?.flags)
    }

    @Test
    fun parseResult_resultCanceled_returnsNull() {
        val parsed = contract.parseResult(Activity.RESULT_CANCELED, Intent())
        assertNull(parsed)
    }

    @Test
    fun parseInitialUriHint_blankOrInvalid_returnsNull() {
        assertNull(parseInitialUriHint(null))
        assertNull(parseInitialUriHint(""))
        assertNull(parseInitialUriHint("   "))
        assertNull(parseInitialUriHint("file:///tmp/tree"))
    }

    @Test
    fun parseInitialUriHint_contentScheme_returnsParsedUri() {
        val hint = "content://com.test/tree/hint"
        assertEquals(Uri.parse(hint), parseInitialUriHint(hint))
    }
}
