// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik START - post-restore verification evidence
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

data class PostRestoreVerificationEvidence(
    val scheduleMinutes: List<Int>,
    val occurrenceCount: Int,
    val answerCount: Int,
    val deletedTextCount: Int,
    val answeredCount: Int,
    val skippedCount: Int,
    val scheduledCount: Int,
    val missedCount: Int,
    val isPracticeStarted: Boolean,
    val currentCycleNumber: Int,
    val nextCyclePosition: Int,
    val activeZoneId: String,
) {
    fun summary(): String {
        return "schedule=$scheduleMinutes;occurrences=$occurrenceCount;answers=$answerCount;" +
            "deletedText=$deletedTextCount;answered=$answeredCount;skipped=$skippedCount;" +
            "scheduled=$scheduledCount;missed=$missedCount;cursor=($currentCycleNumber,$nextCyclePosition)"
    }

    companion object {
        fun fromPayload(payload: PraktikaBackupPayload): PostRestoreVerificationEvidence {
            val answerKeys = payload.answers.map { it.cycleNumber to it.cyclePosition }.toSet()
            val occurrences = payload.occurrences
            return PostRestoreVerificationEvidence(
                scheduleMinutes = payload.scheduleSlots.sortedBy { it.slotIndex }
                    .map { it.timeOfDayMinutes },
                occurrenceCount = occurrences.size,
                answerCount = payload.answers.size,
                deletedTextCount = occurrences.count {
                    it.status == QuestionOccurrenceStatus.ANSWERED.name &&
                        (it.cycleNumber to it.cyclePosition) !in answerKeys
                },
                answeredCount = occurrences.count { it.status == QuestionOccurrenceStatus.ANSWERED.name },
                skippedCount = occurrences.count { it.status == QuestionOccurrenceStatus.SKIPPED_BY_USER.name },
                scheduledCount = occurrences.count { it.status == QuestionOccurrenceStatus.SCHEDULED.name },
                missedCount = occurrences.count { it.status == QuestionOccurrenceStatus.MISSED_BY_TIME.name },
                isPracticeStarted = payload.practiceState.isPracticeStarted,
                currentCycleNumber = payload.practiceState.currentCycleNumber,
                nextCyclePosition = payload.practiceState.nextCyclePosition,
                activeZoneId = payload.practiceState.activeZoneId,
            )
        }
    }
}
// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik END
