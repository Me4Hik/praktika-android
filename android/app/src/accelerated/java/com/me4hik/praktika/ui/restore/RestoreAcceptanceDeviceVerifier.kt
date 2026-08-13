package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.restore.BackupPayloadSemanticComparator
import com.me4hik.praktika.data.backup.restore.PostRestoreDataVerifier
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationEvidence
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult

class RestoreAcceptanceDeviceVerifier(
    private val exporter: RoomBackupExporter,
) : PostRestoreDataVerifier {
    override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
        return when (val exportResult = exporter.export()) {
            is BackupExportResult.Success -> {
                if (!BackupPayloadSemanticComparator.equalsSemantically(
                        restoredEnvelope.payload,
                        exportResult.payload,
                    )
                ) {
                    PostRestoreVerificationResult.Mismatch("payload_semantic_mismatch")
                } else {
                    PostRestoreVerificationResult.Success(
                        evidence = PostRestoreVerificationEvidence.fromPayload(exportResult.payload),
                    )
                }
            }

            is BackupExportResult.DatabaseUnsafe ->
                PostRestoreVerificationResult.Mismatch("export_database_unsafe:${exportResult.reason.name}")

            is BackupExportResult.ReadFailure ->
                PostRestoreVerificationResult.Mismatch("export_read_failure:${exportResult.exceptionClass}")
        }
    }

    fun buildEvidence(exportedPayload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload): PostRestoreVerificationEvidence {
        return PostRestoreVerificationEvidence.fromPayload(exportedPayload)
    }
}
