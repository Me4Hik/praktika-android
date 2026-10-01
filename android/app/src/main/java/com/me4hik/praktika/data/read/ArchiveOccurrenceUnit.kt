// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - one completed occurrence unit
package com.me4hik.praktika.data.read

enum class ArchiveOccurrenceOutcome {
    ANSWERED,
    REJECTED,
    MISSED,
}

data class ArchiveOccurrenceDeferEvent(
    val deferEventId: Long,
    val occurredAtEpochMillis: Long,
    val deferredUntilEpochMillis: Long,
    val durationMinutes: Int,
)

/**
 * One completed [QuestionOccurrence] as the Archive history unit.
 * Defer events are nested; they are not top-level peers.
 */
data class ArchiveOccurrenceUnit(
    val stableKey: String,
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val outcome: ArchiveOccurrenceOutcome,
    val eventAtEpochMillis: Long,
    val answerId: Long?,
    val answerText: String?,
    val deferEvents: List<ArchiveOccurrenceDeferEvent>,
) {
    val deferCount: Int
        get() = deferEvents.size
}
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
