// PROMPT 115 — defer_events joined with occurrence context for archive history
package com.me4hik.praktika.data.local.model

data class ArchiveDeferEventRow(
    val deferEventId: Long,
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val occurredAtEpochMillis: Long,
    val deferredUntilEpochMillis: Long,
    val durationMinutes: Int,
)
