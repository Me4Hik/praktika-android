// PROMPT 115 — mixed archive history read model (no UI strings)
package com.me4hik.praktika.data.read

sealed interface ArchiveHistoryEvent {
    val stableKey: String
    val questionId: Int
    val occurrenceId: Long
    val questionTextSnapshot: String
    val cycleNumber: Int
    val cyclePosition: Int
    val eventAtEpochMillis: Long

    data class Answer(
        override val stableKey: String,
        override val questionId: Int,
        override val occurrenceId: Long,
        override val questionTextSnapshot: String,
        override val cycleNumber: Int,
        override val cyclePosition: Int,
        override val eventAtEpochMillis: Long,
        val answerId: Long?,
        val answerText: String?,
    ) : ArchiveHistoryEvent

    data class Rejected(
        override val stableKey: String,
        override val questionId: Int,
        override val occurrenceId: Long,
        override val questionTextSnapshot: String,
        override val cycleNumber: Int,
        override val cyclePosition: Int,
        override val eventAtEpochMillis: Long,
    ) : ArchiveHistoryEvent

    data class Missed(
        override val stableKey: String,
        override val questionId: Int,
        override val occurrenceId: Long,
        override val questionTextSnapshot: String,
        override val cycleNumber: Int,
        override val cyclePosition: Int,
        override val eventAtEpochMillis: Long,
    ) : ArchiveHistoryEvent

    data class Deferred(
        override val stableKey: String,
        override val questionId: Int,
        override val occurrenceId: Long,
        override val questionTextSnapshot: String,
        override val cycleNumber: Int,
        override val cyclePosition: Int,
        override val eventAtEpochMillis: Long,
        val deferEventId: Long,
        val durationMinutes: Int,
        val deferredUntilEpochMillis: Long,
    ) : ArchiveHistoryEvent
}
