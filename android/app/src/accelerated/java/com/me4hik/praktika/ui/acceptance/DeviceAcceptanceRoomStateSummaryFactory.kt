// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room summary from same PostRestore evidence rules
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationEvidence
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

object DeviceAcceptanceRoomStateSummaryFactory {
    fun fromPayload(payload: PraktikaBackupPayload): DeviceAcceptanceRoomStateSummary {
        val evidence = PostRestoreVerificationEvidence.fromPayload(payload)
        val availableCount = payload.occurrences.count {
            it.status == QuestionOccurrenceStatus.AVAILABLE.name
        }
        return DeviceAcceptanceRoomStateSummary(
            practiceStarted = evidence.isPracticeStarted,
            isPaused = payload.practiceState.isPaused,
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
            availableCount = availableCount,
        )
    }

    fun overlapsBackupSemantics(
        room: DeviceAcceptanceRoomStateSummary,
        backup: DeviceAcceptanceSemanticSummary,
    ): Boolean {
        return room.practiceStarted == backup.practiceStarted &&
            room.isPaused == backup.isPaused &&
            room.currentCycleNumber == backup.currentCycleNumber &&
            room.nextCyclePosition == backup.nextCyclePosition &&
            room.scheduleMinutes == backup.scheduleMinutes &&
            room.occurrenceCount == backup.occurrenceCount &&
            room.answerCount == backup.answerCount &&
            room.deletedTextCount == backup.deletedTextCount &&
            room.answeredCount == backup.answeredCount &&
            room.skippedCount == backup.skippedCount &&
            room.scheduledCount == backup.scheduledCount &&
            room.missedCount == backup.missedCount
    }
}
// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
