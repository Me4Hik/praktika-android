// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.envelope

import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataContext
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator

object BackupEnvelopeAssembler {
    fun assemble(
        payload: PraktikaBackupPayload,
        backupSequence: Long,
        metadata: BackupSnapshotMetadataContext,
    ): BackupEnvelopeAssemblyResult {
        if (backupSequence <= 0L) {
            return BackupEnvelopeAssemblyResult.InvalidSequence
        }
        if (metadata.createdAtEpochMillis <= 0L) {
            return BackupEnvelopeAssemblyResult.InvalidTimestamp
        }
        if (metadata.sourceAppVersionCode <= 0) {
            return BackupEnvelopeAssemblyResult.InvalidAppMetadata
        }
        if (metadata.sourceAppVersionName.isBlank()) {
            return BackupEnvelopeAssemblyResult.InvalidAppMetadata
        }
        if (payload.practiceState.seedVersion <= 0) {
            return BackupEnvelopeAssemblyResult.InvalidSeedVersion
        }

        val sourceSeedVersion = payload.practiceState.seedVersion
        val provisional = PraktikaBackupEnvelope(
            backupSchemaVersion = BackupConstants.BACKUP_SCHEMA_VERSION_V2,
            backupSequence = backupSequence,
            createdAtEpochMillis = metadata.createdAtEpochMillis,
            sourceAppVersionCode = metadata.sourceAppVersionCode,
            sourceAppVersionName = metadata.sourceAppVersionName,
            sourceSeedVersion = sourceSeedVersion,
            backupChecksumSha256 = "",
            payload = payload,
        )

        val checksum = try {
            BackupChecksum.calculate(provisional)
        } catch (throwable: Throwable) {
            return BackupEnvelopeAssemblyResult.IntegrityFailure(throwable.javaClass.name)
        }

        val finalEnvelope = PraktikaBackupEnvelope(
            backupSchemaVersion = provisional.backupSchemaVersion,
            backupSequence = provisional.backupSequence,
            createdAtEpochMillis = provisional.createdAtEpochMillis,
            sourceAppVersionCode = provisional.sourceAppVersionCode,
            sourceAppVersionName = provisional.sourceAppVersionName,
            sourceSeedVersion = provisional.sourceSeedVersion,
            backupChecksumSha256 = checksum,
            payload = payload,
        )

        return when (val validation = BackupFormatValidator.validate(finalEnvelope)) {
            is BackupFormatValidationResult.Valid -> {
                BackupEnvelopeAssemblyResult.Success(finalEnvelope)
            }

            is BackupFormatValidationResult.Invalid -> {
                BackupEnvelopeAssemblyResult.InternalValidationFailure(validation.reason)
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
