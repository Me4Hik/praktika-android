// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - запись export в Uri
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - byte-based writer для CSV BOM
package com.me4hik.praktika.export

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException

interface ExportDocumentWriter {
    suspend fun write(uri: Uri, bytes: ByteArray)
}

class ContentResolverExportDocumentWriter(
    private val contentResolver: ContentResolver,
) : ExportDocumentWriter {
    override suspend fun write(uri: Uri, bytes: ByteArray) {
        val outputStream = contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Unable to open output stream for $uri")
        outputStream.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
