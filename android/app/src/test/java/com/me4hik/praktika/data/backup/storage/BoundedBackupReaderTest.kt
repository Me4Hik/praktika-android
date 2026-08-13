// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage tests
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.BackupConstants
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedBackupReaderTest {
    @Test
    fun emptyInputReturnsSuccessWithEmptyBytes() {
        val result = BoundedBackupReader.read(ByteArrayInputStream(ByteArray(0)))
        assertTrue(result is BoundedBackupReader.ReadResult.Success)
        assertEquals(0, (result as BoundedBackupReader.ReadResult.Success).bytes.size)
    }

    @Test
    fun validSmallInputReturnsBytes() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val result = BoundedBackupReader.read(ByteArrayInputStream(payload))
        assertTrue(result is BoundedBackupReader.ReadResult.Success)
        assertArrayEquals(payload, (result as BoundedBackupReader.ReadResult.Success).bytes)
    }

    @Test
    fun exactlyMaxBytesAllowed() {
        val payload = ByteArray(BackupConstants.MAX_BACKUP_BYTES) { 7 }
        val result = BoundedBackupReader.read(ByteArrayInputStream(payload))
        assertTrue(result is BoundedBackupReader.ReadResult.Success)
        assertEquals(BackupConstants.MAX_BACKUP_BYTES, (result as BoundedBackupReader.ReadResult.Success).bytes.size)
    }

    @Test
    fun maxPlusOneReturnsTooLarge() {
        val payload = ByteArray(BackupConstants.MAX_BACKUP_BYTES + 1) { 9 }
        val result = BoundedBackupReader.read(ByteArrayInputStream(payload))
        assertEquals(BoundedBackupReader.ReadResult.TooLarge, result)
    }

    @Test
    fun chunkBoundaryCrossingDetectsTooLarge() {
        val limit = 16 * 1024
        val payload = ByteArray(limit + 1) { 1 }
        val result = BoundedBackupReader.read(ByteArrayInputStream(payload), maxBytesInclusive = limit)
        assertEquals(BoundedBackupReader.ReadResult.TooLarge, result)
    }

    @Test
    fun streamThrowsReturnsFailed() {
        val result = BoundedBackupReader.read(ThrowingInputStream())
        assertTrue(result is BoundedBackupReader.ReadResult.Failed)
        assertEquals(IOException::class.java.name, (result as BoundedBackupReader.ReadResult.Failed).exceptionClass)
    }

    private class ThrowingInputStream : InputStream() {
        override fun read(): Int = throw IOException("boom")
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
