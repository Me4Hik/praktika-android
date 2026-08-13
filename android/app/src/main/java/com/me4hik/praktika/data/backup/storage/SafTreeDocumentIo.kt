// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.me4hik.praktika.data.backup.BackupConstants

open class SafTreeDocumentIo(
    protected val contentResolver: ContentResolver,
) : SafTreeDocumentAccess {
    override fun queryChildren(treeUri: Uri): List<TreeChildDocument> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val results = mutableListOf<TreeChildDocument>()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val documentIdColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(documentIdColumn) ?: continue
                val displayName = cursor.getString(displayNameColumn) ?: continue
                val mimeType = cursor.getString(mimeTypeColumn)
                val sizeBytes = if (cursor.isNull(sizeColumn)) {
                    null
                } else {
                    cursor.getLong(sizeColumn)
                }
                results += TreeChildDocument(
                    documentId = documentId,
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                )
            }
        }
        return results
    }

    override fun buildDocumentUri(treeUri: Uri, documentId: String): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    override fun readDocumentBounded(documentUri: Uri): BoundedBackupReader.ReadResult {
        val inputStream = contentResolver.openInputStream(documentUri)
            ?: return BoundedBackupReader.ReadResult.Failed("java.io.IOException")
        return BoundedBackupReader.read(inputStream, BackupConstants.MAX_BACKUP_BYTES)
    }

    override fun deleteDocument(documentUri: Uri): Boolean {
        return DocumentsContract.deleteDocument(contentResolver, documentUri)
    }

    override fun createDocument(
        treeUri: Uri,
        mimeType: String,
        displayName: String,
    ): Uri? {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault SAF create parent document URI fix
        val parentDocumentUri = buildTreeRootDocumentUri(treeUri)
        return DocumentsContract.createDocument(
            contentResolver,
            parentDocumentUri,
            mimeType,
            displayName,
        )
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    override fun resolveDisplayName(documentUri: Uri): String? {
        contentResolver.query(documentUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToNext()) {
                    val column = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    return cursor.getString(column)
                }
            }
        return null
    }

    override fun writeDocument(documentUri: Uri, bytes: ByteArray) {
        val outputStream = contentResolver.openOutputStream(documentUri, "wt")
            ?: contentResolver.openOutputStream(documentUri, "w")
            ?: throw java.io.IOException("Unable to open output stream")
        outputStream.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END

// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault SAF create parent document URI fix
internal fun buildTreeRootDocumentUri(treeUri: Uri): Uri {
    val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
