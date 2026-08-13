// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - read-only slot byte → evidence builder
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.codec.BackupJsonDecoder
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.slot.BackupSlotCandidate
import com.me4hik.praktika.data.backup.slot.BackupSlotSelector
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator
import com.me4hik.praktika.data.backup.validate.BackupJsonDecodeResult

sealed class DeviceAcceptanceSlotBytes {
    data object Missing : DeviceAcceptanceSlotBytes()

    data class Ambiguous(val matchCount: Int) : DeviceAcceptanceSlotBytes()

    data object TooLarge : DeviceAcceptanceSlotBytes()

    data class Unreadable(val category: DeviceAcceptanceDecodeStatus) : DeviceAcceptanceSlotBytes()

    data class Readable(val bytes: ByteArray) : DeviceAcceptanceSlotBytes()
}

object DeviceAcceptanceEvidenceBuilder {
    fun buildSlotSummary(
        slot: DeviceAcceptanceSlotId,
        bytes: DeviceAcceptanceSlotBytes,
    ): DeviceAcceptanceSlotSummary {
        return when (bytes) {
            DeviceAcceptanceSlotBytes.Missing -> DeviceAcceptanceSlotSummary(
                slot = slot,
                exists = false,
                byteSize = null,
                fullFileSha256 = null,
                decodeStatus = DeviceAcceptanceDecodeStatus.MISSING,
                checksumValid = null,
                sequence = null,
                createdAtEpochMillis = null,
            )

            is DeviceAcceptanceSlotBytes.Ambiguous -> DeviceAcceptanceSlotSummary(
                slot = slot,
                exists = true,
                byteSize = null,
                fullFileSha256 = null,
                decodeStatus = DeviceAcceptanceDecodeStatus.AMBIGUOUS,
                checksumValid = null,
                sequence = null,
                createdAtEpochMillis = null,
            )

            DeviceAcceptanceSlotBytes.TooLarge -> DeviceAcceptanceSlotSummary(
                slot = slot,
                exists = true,
                byteSize = null,
                fullFileSha256 = null,
                decodeStatus = DeviceAcceptanceDecodeStatus.TOO_LARGE,
                checksumValid = null,
                sequence = null,
                createdAtEpochMillis = null,
            )

            is DeviceAcceptanceSlotBytes.Unreadable -> DeviceAcceptanceSlotSummary(
                slot = slot,
                exists = true,
                byteSize = null,
                fullFileSha256 = null,
                decodeStatus = bytes.category,
                checksumValid = null,
                sequence = null,
                createdAtEpochMillis = null,
            )

            is DeviceAcceptanceSlotBytes.Readable -> {
                val sha = DeviceAcceptanceFolderFingerprint.sha256Hex(bytes.bytes)
                when (val decoded = BackupJsonDecoder.decode(bytes.bytes)) {
                    is BackupJsonDecodeResult.Failure -> DeviceAcceptanceSlotSummary(
                        slot = slot,
                        exists = true,
                        byteSize = bytes.bytes.size.toLong(),
                        fullFileSha256 = sha,
                        decodeStatus = DeviceAcceptanceDecodeStatus.INVALID,
                        checksumValid = null,
                        sequence = null,
                        createdAtEpochMillis = null,
                    )

                    is BackupJsonDecodeResult.Success -> when (
                        val validation = BackupFormatValidator.validate(decoded.envelope)
                    ) {
                        is BackupFormatValidationResult.Valid -> DeviceAcceptanceSlotSummary(
                            slot = slot,
                            exists = true,
                            byteSize = bytes.bytes.size.toLong(),
                            fullFileSha256 = sha,
                            decodeStatus = DeviceAcceptanceDecodeStatus.VALID,
                            checksumValid = true,
                            sequence = validation.envelope.backupSequence,
                            createdAtEpochMillis = validation.envelope.createdAtEpochMillis,
                        )

                        is BackupFormatValidationResult.Invalid -> {
                            val checksumInvalid =
                                validation.reason == BackupFormatFailureReason.ChecksumMismatch ||
                                    validation.reason == BackupFormatFailureReason.InvalidChecksumFormat
                            DeviceAcceptanceSlotSummary(
                                slot = slot,
                                exists = true,
                                byteSize = bytes.bytes.size.toLong(),
                                fullFileSha256 = sha,
                                decodeStatus = if (checksumInvalid) {
                                    DeviceAcceptanceDecodeStatus.CHECKSUM_INVALID
                                } else {
                                    DeviceAcceptanceDecodeStatus.INVALID
                                },
                                checksumValid = false,
                                sequence = null,
                                createdAtEpochMillis = null,
                            )
                        }
                    }
                }
            }
        }
    }

    fun buildFolderEvidence(
        action: String,
        resultStatus: DeviceAcceptanceResultStatus,
        harnessExecutedAtEpochMillis: Long,
        slotABytes: DeviceAcceptanceSlotBytes,
        slotBBytes: DeviceAcceptanceSlotBytes,
        validEnvelopes: Map<DeviceAcceptanceSlotId, PraktikaBackupEnvelope> = emptyMap(),
    ): DeviceAcceptanceEvidenceReport {
        val slotA = buildSlotSummary(DeviceAcceptanceSlotId.A, slotABytes)
        val slotB = buildSlotSummary(DeviceAcceptanceSlotId.B, slotBBytes)
        val envelopes = linkedMapOf<DeviceAcceptanceSlotId, PraktikaBackupEnvelope>()
        envelopes.putAll(validEnvelopes)
        if (slotA.decodeStatus == DeviceAcceptanceDecodeStatus.VALID &&
            slotABytes is DeviceAcceptanceSlotBytes.Readable
        ) {
            decodeValidEnvelope(slotABytes.bytes)?.let { envelopes[DeviceAcceptanceSlotId.A] = it }
        }
        if (slotB.decodeStatus == DeviceAcceptanceDecodeStatus.VALID &&
            slotBBytes is DeviceAcceptanceSlotBytes.Readable
        ) {
            decodeValidEnvelope(slotBBytes.bytes)?.let { envelopes[DeviceAcceptanceSlotId.B] = it }
        }

        val latestSlot = selectLatestValid(envelopes)
        val latestEnvelope = latestSlot?.let { envelopes[it] }
        return DeviceAcceptanceEvidenceReport(
            action = action,
            resultStatus = resultStatus,
            harnessExecutedAtEpochMillis = harnessExecutedAtEpochMillis,
            slotA = slotA,
            slotB = slotB,
            latestValidSlot = latestSlot,
            latestValidSequence = latestEnvelope?.backupSequence,
            latestValidSemantics = latestEnvelope?.let(
                DeviceAcceptanceSemanticSummaryFactory::fromEnvelope,
            ),
            folderFingerprintSha256 = DeviceAcceptanceFolderFingerprint.computeFromSlots(slotA, slotB),
        )
    }

    fun selectLatestValid(
        envelopes: Map<DeviceAcceptanceSlotId, PraktikaBackupEnvelope>,
    ): DeviceAcceptanceSlotId? {
        val candidates = envelopes.map { (slot, envelope) ->
            BackupSlotCandidate(
                slotId = when (slot) {
                    DeviceAcceptanceSlotId.A -> BackupSlotId.A
                    DeviceAcceptanceSlotId.B -> BackupSlotId.B
                },
                validation = BackupFormatValidationResult.Valid(envelope),
            )
        }
        val best = BackupSlotSelector.selectBest(candidates) ?: return null
        return when (best) {
            BackupSlotId.A -> DeviceAcceptanceSlotId.A
            BackupSlotId.B -> DeviceAcceptanceSlotId.B
        }
    }

    private fun decodeValidEnvelope(bytes: ByteArray): PraktikaBackupEnvelope? {
        return when (val decoded = BackupJsonDecoder.decode(bytes)) {
            is BackupJsonDecodeResult.Failure -> null
            is BackupJsonDecodeResult.Success -> when (
                val validation = BackupFormatValidator.validate(decoded.envelope)
            ) {
                is BackupFormatValidationResult.Valid -> validation.envelope
                is BackupFormatValidationResult.Invalid -> null
            }
        }
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
