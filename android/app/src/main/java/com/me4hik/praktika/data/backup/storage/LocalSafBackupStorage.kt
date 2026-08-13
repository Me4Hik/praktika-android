// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 cancellation hardening
package com.me4hik.praktika.data.backup.storage

import android.net.Uri
import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.codec.BackupJsonDecoder
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator
import com.me4hik.praktika.data.backup.validate.BackupJsonDecodeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class LocalSafBackupStorage(
    private val treeUri: Uri,
    private val treeQuery: SafTreeDocumentAccess,
    private val accessValidator: BackupTreeAccessValidator,
) : BackupStorageProvider {
    override suspend fun inspectSlots(): SlotInspectionResult = withContext(Dispatchers.IO) {
        when (val access = accessValidator.validate(treeUri)) {
            BackupFolderAccessState.PermissionLost -> disconnectedInspection(SecurityException::class.java.name)
            BackupFolderAccessState.Unavailable -> disconnectedInspection(IllegalStateException::class.java.name)
            is BackupFolderAccessState.ProviderError -> disconnectedInspection(access.exceptionClass)
            is BackupFolderAccessState.Connected -> {
                coroutineContext.ensureActive()
                val slotA = readSlotInternal(BackupSlotId.A)
                coroutineContext.ensureActive()
                val slotB = readSlotInternal(BackupSlotId.B)
                coroutineContext.ensureActive()
                SlotInspectionResult(slotA = slotA, slotB = slotB)
            }
        }
    }

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult = withContext(Dispatchers.IO) {
        when (val access = accessValidator.validate(treeUri)) {
            BackupFolderAccessState.PermissionLost ->
                SlotReadResult.Unreadable(slot, SecurityException::class.java.name)
            BackupFolderAccessState.Unavailable ->
                SlotReadResult.Unreadable(slot, IllegalStateException::class.java.name)
            is BackupFolderAccessState.ProviderError ->
                SlotReadResult.Unreadable(slot, access.exceptionClass)
            is BackupFolderAccessState.Connected -> readSlotInternal(slot)
        }
    }

    override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult = withContext(Dispatchers.IO) {
        if (bytes.size > BackupConstants.MAX_BACKUP_BYTES) {
            return@withContext BackupSlotWriteResult.TooLarge
        }

        when (val access = accessValidator.validate(treeUri)) {
            BackupFolderAccessState.PermissionLost -> return@withContext BackupSlotWriteResult.PermissionLost
            BackupFolderAccessState.Unavailable -> return@withContext BackupSlotWriteResult.Unavailable
            is BackupFolderAccessState.ProviderError ->
                return@withContext BackupSlotWriteResult.ProviderFailure(slot, access.exceptionClass)
            is BackupFolderAccessState.Connected -> Unit
        }

        try {
            writeSlotInternal(slot, bytes, expectedEnvelope)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (securityException: SecurityException) {
            BackupSlotWriteResult.PermissionLost
        } catch (exception: Exception) {
            BackupSlotWriteResult.ProviderFailure(slot, exception.javaClass.name)
        }
    }

    private fun disconnectedInspection(exceptionClass: String): SlotInspectionResult {
        return SlotInspectionResult(
            slotA = SlotReadResult.Unreadable(BackupSlotId.A, exceptionClass),
            slotB = SlotReadResult.Unreadable(BackupSlotId.B, exceptionClass),
        )
    }

    private suspend fun readSlotInternal(slot: BackupSlotId): SlotReadResult {
        return try {
            val result = when (val match = discoverSlot(slot)) {
                SlotDocumentMatch.Missing -> SlotReadResult.Missing
                is SlotDocumentMatch.Ambiguous -> SlotReadResult.Ambiguous(slot, match.matchCount)
                is SlotDocumentMatch.Located -> decodeLocatedSlot(slot, match.document)
            }
            coroutineContext.ensureActive()
            result
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (securityException: SecurityException) {
            SlotReadResult.Unreadable(slot, securityException.javaClass.name)
        } catch (exception: Exception) {
            SlotReadResult.Unreadable(slot, exception.javaClass.name)
        }
    }

    private fun decodeLocatedSlot(slot: BackupSlotId, document: TreeChildDocument): SlotReadResult {
        val documentUri = treeQuery.buildDocumentUri(treeUri, document.documentId)
        if (document.sizeBytes != null && document.sizeBytes > BackupConstants.MAX_BACKUP_BYTES) {
            return SlotReadResult.TooLarge(slot)
        }
        return when (val readResult = treeQuery.readDocumentBounded(documentUri)) {
            BoundedBackupReader.ReadResult.TooLarge -> SlotReadResult.TooLarge(slot)
            is BoundedBackupReader.ReadResult.Failed ->
                SlotReadResult.Unreadable(slot, readResult.exceptionClass)
            is BoundedBackupReader.ReadResult.Success -> decodeBytes(slot, readResult.bytes)
        }
    }

    private fun decodeBytes(slot: BackupSlotId, bytes: ByteArray): SlotReadResult {
        return when (val decodeResult = BackupJsonDecoder.decode(bytes)) {
            is BackupJsonDecodeResult.Failure -> SlotReadResult.Invalid(slot, decodeResult.reason)
            is BackupJsonDecodeResult.Success -> when (val validation = BackupFormatValidator.validate(decodeResult.envelope)) {
                is BackupFormatValidationResult.Invalid ->
                    SlotReadResult.Invalid(slot, validation.reason)
                is BackupFormatValidationResult.Valid ->
                    SlotReadResult.Valid(slot, validation.envelope)
            }
        }
    }

    private fun discoverSlot(slot: BackupSlotId): SlotDocumentMatch {
        val children = treeQuery.queryChildren(treeUri)
        return SlotDocumentDiscovery.matchSlot(children, slot)
    }

    private suspend fun writeSlotInternal(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult {
        when (val preWriteMatch = discoverSlot(slot)) {
            is SlotDocumentMatch.Ambiguous ->
                return BackupSlotWriteResult.SlotAmbiguous(slot, preWriteMatch.matchCount)
            is SlotDocumentMatch.Located -> {
                coroutineContext.ensureActive()
                val existingUri = treeQuery.buildDocumentUri(treeUri, preWriteMatch.document.documentId)
                if (!treeQuery.deleteDocument(existingUri)) {
                    return BackupSlotWriteResult.DeleteFailed(slot)
                }
                when (val afterDelete = discoverSlot(slot)) {
                    SlotDocumentMatch.Missing -> Unit
                    is SlotDocumentMatch.Ambiguous ->
                        return BackupSlotWriteResult.SlotAmbiguous(slot, afterDelete.matchCount)
                    is SlotDocumentMatch.Located ->
                        return BackupSlotWriteResult.DeleteFailed(slot)
                }
            }
            SlotDocumentMatch.Missing -> Unit
        }

        coroutineContext.ensureActive()
        val createdUri = treeQuery.createDocument(
            treeUri = treeUri,
            mimeType = BACKUP_MIME_TYPE,
            displayName = slot.fileName,
        ) ?: return BackupSlotWriteResult.CreateFailed(slot)

        val actualName = treeQuery.resolveDisplayName(createdUri)
        if (actualName != slot.fileName) {
            return BackupSlotWriteResult.NameMismatch(slot, actualName ?: "")
        }

        coroutineContext.ensureActive()
        try {
            treeQuery.writeDocument(createdUri, bytes)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            return BackupSlotWriteResult.WriteFailed(slot, exception.javaClass.name)
        }

        return verifyPostWrite(slot, expectedEnvelope)
    }

    private suspend fun verifyPostWrite(
        slot: BackupSlotId,
        expectedEnvelope: PraktikaBackupEnvelope,
    ): BackupSlotWriteResult {
        return when (val readBack = readSlotInternal(slot)) {
            SlotReadResult.Missing,
            is SlotReadResult.Ambiguous,
            is SlotReadResult.Unreadable,
            is SlotReadResult.TooLarge,
            -> BackupSlotWriteResult.PostWriteReadFailed(slot)
            is SlotReadResult.Invalid -> BackupSlotWriteResult.PostWriteValidationFailed(slot)
            is SlotReadResult.Valid -> {
                when (
                    val identity = PostWriteEnvelopeVerifier.verify(
                        PostWriteEnvelopeVerifier.expectedFrom(expectedEnvelope),
                        readBack.envelope,
                    )
                ) {
                    PostWriteEnvelopeVerifier.VerificationResult.Success -> {
                        coroutineContext.ensureActive()
                        BackupSlotWriteResult.Success(
                            slot = slot,
                            sequence = readBack.envelope.backupSequence,
                            checksum = readBack.envelope.backupChecksumSha256,
                        )
                    }
                    is PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch ->
                        BackupSlotWriteResult.ExpectedIdentityMismatch(slot, identity.field)
                }
            }
        }
    }

    private companion object {
        const val BACKUP_MIME_TYPE = "application/json"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
