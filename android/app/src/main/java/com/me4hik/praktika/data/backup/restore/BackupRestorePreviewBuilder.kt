// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Coordinator
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

object BackupRestorePreviewBuilder {
    private val terminalStatuses = setOf(
        QuestionOccurrenceStatus.ANSWERED,
        QuestionOccurrenceStatus.SKIPPED_BY_USER,
        QuestionOccurrenceStatus.MISSED_BY_TIME,
    )

    fun fromEnvelope(
        slot: BackupSlotId,
        envelope: PraktikaBackupEnvelope,
    ): BackupRestorePreview {
        val payload = envelope.payload
        val answerKeys = payload.answers.map { it.cycleNumber to it.cyclePosition }.toSet()
        val occurrences = payload.occurrences

        return BackupRestorePreview(
            selectedSlot = slot,
            backupSequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceAppVersionCode = envelope.sourceAppVersionCode,
            sourceAppVersionName = envelope.sourceAppVersionName,
            sourceSeedVersion = envelope.sourceSeedVersion,
            answerCount = payload.answers.size,
            completedSlots = countCompletedSlots(occurrences),
            deletedTextCount = countDeletedTextOccurrences(occurrences, answerKeys),
            occurrenceCount = occurrences.size,
            isPracticeStarted = payload.practiceState.isPracticeStarted,
            identity = BackupRestoreSelectedIdentity.from(slot, envelope),
        )
    }

    private fun countCompletedSlots(occurrences: List<BackupOccurrence>): Int {
        return occurrences.count { occurrence ->
            val status = runCatching { QuestionOccurrenceStatus.valueOf(occurrence.status) }.getOrNull()
            status in terminalStatuses
        }
    }

    private fun countDeletedTextOccurrences(
        occurrences: List<BackupOccurrence>,
        answerKeys: Set<Pair<Int, Int>>,
    ): Int {
        return occurrences.count { occurrence ->
            occurrence.status == QuestionOccurrenceStatus.ANSWERED.name &&
                (occurrence.cycleNumber to occurrence.cyclePosition) !in answerKeys
        }
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
