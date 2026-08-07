// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik START - test fake export writer
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik START - byte-based recording writer
package com.me4hik.praktika.ui.archive

import android.net.Uri
import com.me4hik.praktika.export.ExportDocumentWriter

internal class RecordingExportDocumentWriter(
    private val shouldFail: Boolean,
) : ExportDocumentWriter {
    var lastBytes: ByteArray? = null

    override suspend fun write(uri: Uri, bytes: ByteArray) {
        if (shouldFail) {
            throw java.io.IOException("write failed")
        }
        lastBytes = bytes.copyOf()
    }
}
// 07.08.2026 Stage 20 CSV Export cursor by Me4Hik END
// 07.08.2026 Stage 19 Markdown Export cursor by Me4Hik END
