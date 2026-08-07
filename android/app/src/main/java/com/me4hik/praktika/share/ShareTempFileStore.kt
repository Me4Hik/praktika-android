// 07.08.2026 Stage 21 Share cursor by Me4Hik START - temp cache files для file share
package com.me4hik.praktika.share

import android.content.Context
import com.me4hik.praktika.export.ExportDocument
import java.io.File
import java.io.IOException
import java.util.UUID

class ShareTempFileStore(
    private val shareRoot: File,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun write(document: ExportDocument): File {
        cleanupOldSessions()
        val sessionDir = File(shareRoot, UUID.randomUUID().toString())
        if (!sessionDir.mkdirs() && !sessionDir.isDirectory) {
            throw IOException("Unable to create share session directory: $sessionDir")
        }
        val file = File(sessionDir, document.suggestedFileName)
        file.writeBytes(document.bytes)
        return file
    }

    fun cleanupOldSessions(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        if (!shareRoot.exists()) {
            return
        }
        val cutoff = clockMillis() - maxAgeMillis
        shareRoot.listFiles()?.forEach { sessionDir ->
            if (sessionDir.isDirectory && sessionDir.lastModified() < cutoff) {
                sessionDir.deleteRecursively()
            }
        }
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private const val SHARE_DIR_NAME = "share"

        fun fromContext(context: Context): ShareTempFileStore {
            return ShareTempFileStore(File(context.cacheDir, SHARE_DIR_NAME))
        }
    }
}
// 07.08.2026 Stage 21 Share cursor by Me4Hik END
