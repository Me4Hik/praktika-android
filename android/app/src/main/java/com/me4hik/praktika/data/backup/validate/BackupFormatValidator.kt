// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - backup format metadata and checksum validator
package com.me4hik.praktika.data.backup.validate

import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.integrity.BackupIntegrityEncoders
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

object BackupFormatValidator {
    private val CHECKSUM_HEX_PATTERN = Regex("^[0-9a-f]{${BackupConstants.CHECKSUM_HEX_LENGTH}}$")

    fun validate(envelope: PraktikaBackupEnvelope): BackupFormatValidationResult {
        if (envelope.backupSchemaVersion < BackupConstants.MIN_SUPPORTED_SCHEMA_VERSION ||
            envelope.backupSchemaVersion > BackupConstants.MAX_SUPPORTED_SCHEMA_VERSION
        ) {
            return invalid(BackupFormatFailureReason.UnsupportedSchema, "Unsupported backup schema version")
        }

        if (envelope.backupSequence <= 0L) {
            return invalid(BackupFormatFailureReason.InvalidMetadata, "backupSequence must be positive")
        }

        if (envelope.createdAtEpochMillis <= 0L) {
            return invalid(BackupFormatFailureReason.InvalidMetadata, "createdAtEpochMillis must be positive")
        }

        if (envelope.sourceAppVersionCode <= 0) {
            return invalid(BackupFormatFailureReason.InvalidMetadata, "sourceAppVersionCode must be positive")
        }

        if (envelope.sourceAppVersionName.isBlank()) {
            return invalid(BackupFormatFailureReason.InvalidMetadata, "sourceAppVersionName must be non-blank")
        }

        if (envelope.sourceSeedVersion <= 0) {
            return invalid(BackupFormatFailureReason.InvalidMetadata, "sourceSeedVersion must be positive")
        }

        if (!CHECKSUM_HEX_PATTERN.matches(envelope.backupChecksumSha256)) {
            return invalid(BackupFormatFailureReason.InvalidChecksumFormat, "Invalid checksum format")
        }

        if (BackupIntegrityEncoders.forSchemaVersion(envelope.backupSchemaVersion) == null) {
            return invalid(BackupFormatFailureReason.UnsupportedSchema, "No integrity encoder for schema version")
        }

        val expectedChecksum = BackupChecksum.calculate(envelope)
        if (!constantTimeEquals(expectedChecksum, envelope.backupChecksumSha256)) {
            return invalid(BackupFormatFailureReason.ChecksumMismatch, "Checksum mismatch")
        }

        return BackupFormatValidationResult.Valid(envelope)
    }

    private fun invalid(reason: BackupFormatFailureReason, detail: String): BackupFormatValidationResult.Invalid {
        return BackupFormatValidationResult.Invalid(reason, detail)
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) {
            return false
        }
        var mismatch = 0
        for (index in left.indices) {
            mismatch = mismatch or (left[index].code xor right[index].code)
        }
        return mismatch == 0
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
