// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

object PostWriteEnvelopeVerifier {
    data class ExpectedIdentity(
        val backupSchemaVersion: Int,
        val backupSequence: Long,
        val backupChecksumSha256: String,
        val createdAtEpochMillis: Long,
    )

    sealed class VerificationResult {
        data object Success : VerificationResult()

        data class ExpectedIdentityMismatch(val field: String) : VerificationResult()
    }

    fun verify(
        expected: ExpectedIdentity,
        actual: PraktikaBackupEnvelope,
    ): VerificationResult {
        if (actual.backupSchemaVersion != expected.backupSchemaVersion) {
            return VerificationResult.ExpectedIdentityMismatch("backupSchemaVersion")
        }
        if (actual.backupSequence != expected.backupSequence) {
            return VerificationResult.ExpectedIdentityMismatch("backupSequence")
        }
        if (actual.backupChecksumSha256 != expected.backupChecksumSha256) {
            return VerificationResult.ExpectedIdentityMismatch("backupChecksumSha256")
        }
        if (actual.createdAtEpochMillis != expected.createdAtEpochMillis) {
            return VerificationResult.ExpectedIdentityMismatch("createdAtEpochMillis")
        }
        return VerificationResult.Success
    }

    fun expectedFrom(envelope: PraktikaBackupEnvelope): ExpectedIdentity {
        return ExpectedIdentity(
            backupSchemaVersion = envelope.backupSchemaVersion,
            backupSequence = envelope.backupSequence,
            backupChecksumSha256 = envelope.backupChecksumSha256,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
