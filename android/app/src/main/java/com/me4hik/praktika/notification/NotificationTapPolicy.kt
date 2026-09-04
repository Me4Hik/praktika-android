// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - stale guard для tap
package com.me4hik.praktika.notification

import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

sealed interface NotificationTapDecision {
    data class Open(val occurrenceId: Long) : NotificationTapDecision
    data object Ignore : NotificationTapDecision
}

sealed interface NotificationDeferDecision {
    data class Deferred(val durationMinutes: Int) : NotificationDeferDecision
    data object Ignore : NotificationDeferDecision
}

object NotificationTapPolicy {
    fun evaluate(
        practiceState: PracticeStateEntity,
        currentIncomplete: QuestionOccurrenceEntity?,
        targetOccurrence: QuestionOccurrenceEntity?,
        expectedOccurrenceId: Long,
        expectedPlannedAtEpochMillis: Long,
    ): NotificationTapDecision {
        if (!practiceState.isPracticeStarted || practiceState.isPaused) {
            return NotificationTapDecision.Ignore
        }
        val occurrence = targetOccurrence ?: return NotificationTapDecision.Ignore
        if (occurrence.id != expectedOccurrenceId) {
            return NotificationTapDecision.Ignore
        }
        if (occurrence.plannedAtEpochMillis != expectedPlannedAtEpochMillis) {
            return NotificationTapDecision.Ignore
        }
        if (currentIncomplete?.id != occurrence.id) {
            return NotificationTapDecision.Ignore
        }
        if (occurrence.status != QuestionOccurrenceStatus.AVAILABLE) {
            return NotificationTapDecision.Ignore
        }
        // 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - ignore repeat tap after openedAt set
        if (occurrence.openedAtEpochMillis != null) {
            return NotificationTapDecision.Ignore
        }
        // 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
        return NotificationTapDecision.Open(occurrence.id)
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
