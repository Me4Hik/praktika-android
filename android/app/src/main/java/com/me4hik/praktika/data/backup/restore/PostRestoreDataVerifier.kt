// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope

sealed interface PostRestoreVerificationResult {
    data object NotRequested : PostRestoreVerificationResult

    data class Success(
        val evidence: PostRestoreVerificationEvidence? = null,
    ) : PostRestoreVerificationResult

    data class Mismatch(
        val detail: String,
    ) : PostRestoreVerificationResult
}

fun interface PostRestoreDataVerifier {
    suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult
}

object NoOpPostRestoreDataVerifier : PostRestoreDataVerifier {
    override suspend fun verify(restoredEnvelope: PraktikaBackupEnvelope): PostRestoreVerificationResult {
        return PostRestoreVerificationResult.NotRequested
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
