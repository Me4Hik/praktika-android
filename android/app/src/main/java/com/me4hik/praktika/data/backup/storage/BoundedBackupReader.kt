// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.BackupConstants
import java.io.ByteArrayOutputStream
import java.io.InputStream

object BoundedBackupReader {
    private const val CHUNK_SIZE = 8 * 1024

    sealed class ReadResult {
        data class Success(val bytes: ByteArray) : ReadResult()

        data object TooLarge : ReadResult()

        data class Failed(val exceptionClass: String) : ReadResult()
    }

    fun read(input: InputStream, maxBytesInclusive: Int = BackupConstants.MAX_BACKUP_BYTES): ReadResult {
        return try {
            input.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(CHUNK_SIZE)
                var total = 0
                while (true) {
                    val readCount = stream.read(chunk)
                    if (readCount <= 0) {
                        break
                    }
                    total += readCount
                    if (total > maxBytesInclusive) {
                        return ReadResult.TooLarge
                    }
                    buffer.write(chunk, 0, readCount)
                }
                ReadResult.Success(buffer.toByteArray())
            }
        } catch (exception: Exception) {
            ReadResult.Failed(exception.javaClass.name)
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
