// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening tests
package com.me4hik.praktika.data.backup.storage

import android.net.Uri
import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.model.BackupSlotId
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LocalSafBackupStorageCancellationTest {
    private val treeUri = testTreeUri()
    private val goldenEnvelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
    private val goldenBytes = BackupJsonEncoder.encodeToUtf8Bytes(goldenEnvelope)

    private fun storage(fake: FakeSafTreeDocumentIo): LocalSafBackupStorage {
        return LocalSafBackupStorage(
            treeUri = treeUri,
            treeQuery = fake,
            accessValidator = ConnectedAccessValidator(treeUri),
        )
    }

    @Test
    fun write_cancelDuringPhysicalWrite_propagatesCancellationException() = runTest {
        val fake = FakeSafTreeDocumentIo()
        fake.writeHook = { throw CancellationException("cancelled during write") }

        val thrown = runCatching {
            storage(fake).writeSlot(BackupSlotId.A, goldenBytes, goldenEnvelope)
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun write_noSuccessAfterCancellationRequested() = runTest {
        val fake = FakeSafTreeDocumentIo()
        fake.writeProceedLatch = CountDownLatch(1)
        val job = async {
            storage(fake).writeSlot(BackupSlotId.A, goldenBytes, goldenEnvelope)
        }
        fake.writeStarted.await()
        job.cancel()
        fake.writeProceedLatch!!.countDown()

        val thrown = runCatching { job.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun write_noSuccessAfterPipelineBlockedThenCancelled() = runTest {
        val fake = FakeSafTreeDocumentIo()
        fake.writeProceedLatch = CountDownLatch(1)
        val job = async {
            storage(fake).writeSlot(BackupSlotId.A, goldenBytes, goldenEnvelope)
        }
        fake.writeStarted.await()
        job.cancel()
        fake.writeProceedLatch!!.countDown()

        val thrown = runCatching { job.await() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun read_queryThrowsCancellationException_propagates() = runTest {
        val fake = FakeSafTreeDocumentIo()
        fake.queryChildrenHook = { throw CancellationException("cancelled during query") }

        val thrown = runCatching {
            storage(fake).readSlot(BackupSlotId.A)
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun read_ioExceptionStillUnreadable() = runTest {
        val fake = FakeSafTreeDocumentIo()
        fake.queryChildrenHook = { throw IOException("read failed") }

        val result = storage(fake).readSlot(BackupSlotId.A)

        assertTrue(result is SlotReadResult.Unreadable)
        assertEquals(IOException::class.java.name, (result as SlotReadResult.Unreadable).exceptionClass)
    }

    @Test
    fun write_ioExceptionStillWriteFailed() = runTest {
        val fake = FakeSafTreeDocumentIo()
        fake.writeHook = { throw IOException("write failed") }

        val result = storage(fake).writeSlot(BackupSlotId.A, goldenBytes, goldenEnvelope)

        assertTrue(result is BackupSlotWriteResult.WriteFailed)
        assertEquals(IOException::class.java.name, (result as BackupSlotWriteResult.WriteFailed).exceptionClass)
    }

    @Test
    fun write_securityExceptionStillPermissionLost() = runTest {
        val fake = FakeSafTreeDocumentIo()
        val deniedStorage = LocalSafBackupStorage(
            treeUri = treeUri,
            treeQuery = fake,
            accessValidator = PermissionLostAccessValidator(),
        )

        val result = deniedStorage.writeSlot(BackupSlotId.A, goldenBytes, goldenEnvelope)

        assertEquals(BackupSlotWriteResult.PermissionLost, result)
    }

    @Test
    fun write_successWhenNotCancelled() = runTest {
        val fake = FakeSafTreeDocumentIo()
        val result = storage(fake).writeSlot(BackupSlotId.A, goldenBytes, goldenEnvelope)

        assertTrue(result is BackupSlotWriteResult.Success)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
