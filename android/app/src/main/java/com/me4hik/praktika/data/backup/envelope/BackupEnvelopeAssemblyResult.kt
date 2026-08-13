// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.envelope

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason

sealed interface BackupEnvelopeAssemblyResult {
    data class Success(
        val envelope: PraktikaBackupEnvelope,
    ) : BackupEnvelopeAssemblyResult

    data object InvalidSequence : BackupEnvelopeAssemblyResult

    data object InvalidTimestamp : BackupEnvelopeAssemblyResult

    data object InvalidAppMetadata : BackupEnvelopeAssemblyResult

    data object InvalidSeedVersion : BackupEnvelopeAssemblyResult

    data class IntegrityFailure(
        val exceptionClass: String,
    ) : BackupEnvelopeAssemblyResult

    data class InternalValidationFailure(
        val reason: BackupFormatFailureReason,
    ) : BackupEnvelopeAssemblyResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
