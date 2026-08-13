// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening
package com.me4hik.praktika.data.backup.storage

import android.net.Uri

fun interface BackupTreeAccessValidator {
    fun validate(treeUri: Uri): BackupFolderAccessState
}

interface SafTreeDocumentAccess {
    fun queryChildren(treeUri: Uri): List<TreeChildDocument>

    fun buildDocumentUri(treeUri: Uri, documentId: String): Uri

    fun readDocumentBounded(documentUri: Uri): BoundedBackupReader.ReadResult

    fun deleteDocument(documentUri: Uri): Boolean

    fun createDocument(
        treeUri: Uri,
        mimeType: String,
        displayName: String,
    ): Uri?

    fun resolveDisplayName(documentUri: Uri): String?

    fun writeDocument(documentUri: Uri, bytes: ByteArray)
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
