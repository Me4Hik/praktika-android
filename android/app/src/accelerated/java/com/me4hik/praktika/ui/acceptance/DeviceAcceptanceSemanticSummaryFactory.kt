// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - safe semantic summary from envelope
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationEvidence

object DeviceAcceptanceSemanticSummaryFactory {
    fun fromEnvelope(envelope: PraktikaBackupEnvelope): DeviceAcceptanceSemanticSummary {
        val evidence = PostRestoreVerificationEvidence.fromPayload(envelope.payload)
        return DeviceAcceptanceSemanticSummary(
            backupSchemaVersion = envelope.backupSchemaVersion,
            sequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceVersionCode = envelope.sourceAppVersionCode,
            sourceVersionName = envelope.sourceAppVersionName,
            practiceStarted = evidence.isPracticeStarted,
            isPaused = envelope.payload.practiceState.isPaused,
            currentCycleNumber = evidence.currentCycleNumber,
            nextCyclePosition = evidence.nextCyclePosition,
            scheduleMinutes = evidence.scheduleMinutes,
            occurrenceCount = evidence.occurrenceCount,
            answerCount = evidence.answerCount,
            deletedTextCount = evidence.deletedTextCount,
            answeredCount = evidence.answeredCount,
            skippedCount = evidence.skippedCount,
            scheduledCount = evidence.scheduledCount,
            missedCount = evidence.missedCount,
        )
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
