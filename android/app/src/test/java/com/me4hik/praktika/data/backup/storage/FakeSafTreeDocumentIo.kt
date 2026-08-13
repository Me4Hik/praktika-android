// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening tests
package com.me4hik.praktika.data.backup.storage

import android.net.Uri
import com.me4hik.praktika.data.backup.model.BackupSlotId
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred

internal class FakeSafTreeDocumentIo : SafTreeDocumentAccess {
    private val childrenByTree = mutableMapOf<Uri, MutableList<TreeChildDocument>>()
    private val bytesByDocumentUri = mutableMapOf<Uri, ByteArray>()
    private val displayNameByDocumentUri = mutableMapOf<Uri, String>()

    var queryChildrenHook: (() -> Unit)? = null
    var writeHook: (() -> Unit)? = null
    var writeProceedLatch: CountDownLatch? = null
    val writeStarted = CompletableDeferred<Unit>()

    override fun queryChildren(treeUri: Uri): List<TreeChildDocument> {
        queryChildrenHook?.invoke()
        return childrenByTree[treeUri].orEmpty().toList()
    }

    override fun buildDocumentUri(treeUri: Uri, documentId: String): Uri {
        return testTreeUri("$treeUri/document/$documentId")
    }

    override fun createDocument(
        treeUri: Uri,
        mimeType: String,
        displayName: String,
    ): Uri? {
        val documentId = "doc-${displayName.hashCode()}"
        val documentUri = buildDocumentUri(treeUri, documentId)
        childrenByTree.getOrPut(treeUri) { mutableListOf() }.add(
            TreeChildDocument(
                documentId = documentId,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = null,
            ),
        )
        displayNameByDocumentUri[documentUri] = displayName
        return documentUri
    }

    override fun resolveDisplayName(documentUri: Uri): String? {
        return displayNameByDocumentUri[documentUri]
    }

    override fun deleteDocument(documentUri: Uri): Boolean {
        val treeUri = treeUriFromDocument(documentUri)
        val documents = childrenByTree[treeUri]
        val removed = documents?.removeIf { buildDocumentUri(treeUri, it.documentId) == documentUri } == true
        bytesByDocumentUri.remove(documentUri)
        displayNameByDocumentUri.remove(documentUri)
        return removed
    }

    override fun writeDocument(documentUri: Uri, bytes: ByteArray) {
        writeStarted.complete(Unit)
        writeProceedLatch?.await(5, TimeUnit.SECONDS)
        writeHook?.invoke()
        bytesByDocumentUri[documentUri] = bytes.copyOf()
        updateDocumentSize(documentUri, bytes.size.toLong())
    }

    override fun readDocumentBounded(documentUri: Uri): BoundedBackupReader.ReadResult {
        val bytes = bytesByDocumentUri[documentUri]
            ?: return BoundedBackupReader.ReadResult.Failed(IOException::class.java.name)
        return BoundedBackupReader.ReadResult.Success(bytes)
    }

    fun seedValidSlot(treeUri: Uri, slot: BackupSlotId, bytes: ByteArray) {
        val documentId = "seed-${slot.name}"
        val documentUri = buildDocumentUri(treeUri, documentId)
        childrenByTree.getOrPut(treeUri) { mutableListOf() }.add(
            TreeChildDocument(
                documentId = documentId,
                displayName = slot.fileName,
                mimeType = "application/json",
                sizeBytes = bytes.size.toLong(),
            ),
        )
        displayNameByDocumentUri[documentUri] = slot.fileName
        bytesByDocumentUri[documentUri] = bytes.copyOf()
    }

    private fun updateDocumentSize(documentUri: Uri, sizeBytes: Long) {
        val treeUri = treeUriFromDocument(documentUri)
        val documents = childrenByTree[treeUri] ?: return
        documents.forEachIndexed { index, document ->
            val uri = buildDocumentUri(treeUri, document.documentId)
            if (uri == documentUri) {
                documents[index] = document.copy(sizeBytes = sizeBytes)
            }
        }
    }

    private fun treeUriFromDocument(documentUri: Uri): Uri {
        return testTreeUri(documentUri.toString().substringBefore("/document/"))
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
