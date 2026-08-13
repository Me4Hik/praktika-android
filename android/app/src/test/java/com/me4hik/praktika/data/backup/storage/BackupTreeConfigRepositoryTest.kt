// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage tests
package com.me4hik.praktika.data.backup.storage

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTreeConfigRepositoryTest {
    @Test
    fun saveAndReadHint() = runBlocking {
        val repository = InMemoryBackupTreeConfigRepository()
        assertNull(repository.treeUriHint.first())
        repository.saveTreeUri("content://tree/example")
        assertEquals("content://tree/example", repository.treeUriHint.first())
    }

    @Test
    fun clearHintRemovesStoredValue() = runBlocking {
        val repository = InMemoryBackupTreeConfigRepository()
        repository.saveTreeUri("content://tree/example")
        repository.clearTreeUri()
        assertNull(repository.treeUriHint.first())
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
