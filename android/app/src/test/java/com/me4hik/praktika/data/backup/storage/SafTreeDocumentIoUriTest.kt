// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault SAF create parent document URI fix
package com.me4hik.praktika.data.backup.storage

import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SafTreeDocumentIoUriTest {
    private val encodedTreeUri = Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3APraktika_backup_test",
    )

    @Test
    fun encodedColon_isValidTreeUri() {
        assertTrue(DocumentsContract.isTreeUri(encodedTreeUri))
    }

    @Test
    fun treeDocumentId_decodedByAndroidApi() {
        assertEquals(
            "primary:Praktika_backup_test",
            DocumentsContract.getTreeDocumentId(encodedTreeUri),
        )
    }

    @Test
    fun rootDocumentUri_builtWithDocumentsContract() {
        val rootDocumentUri = buildTreeRootDocumentUri(encodedTreeUri)

        assertNotEquals(encodedTreeUri, rootDocumentUri)
        assertEquals(
            DocumentsContract.getTreeDocumentId(encodedTreeUri),
            DocumentsContract.getDocumentId(rootDocumentUri),
        )
        assertTrue(rootDocumentUri.toString().contains("/document/"))
    }

    @Test
    fun rawTreeUri_notUsedAsCreateParent_contract() {
        val rootDocumentUri = buildTreeRootDocumentUri(encodedTreeUri)

        assertTrue(DocumentsContract.isTreeUri(encodedTreeUri))
        assertNotEquals("create parent must not be raw tree URI", encodedTreeUri, rootDocumentUri)
        assertFalse(
            "old bug passed tree URI where document parent URI is required",
            encodedTreeUri == rootDocumentUri,
        )
    }

    @Test
    fun queryChildren_usesChildDocumentsUriWithEncodedTreeId() {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(encodedTreeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            encodedTreeUri,
            treeDocumentId,
        )

        assertTrue(childrenUri.toString().contains("children"))
        assertEquals("primary:Praktika_backup_test", treeDocumentId)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
