// PROMPT 111 — portable defer event DTO for Data Vault V2
package com.me4hik.praktika.data.backup.model

class BackupDeferEvent(
    val cycleNumber: Int,
    val cyclePosition: Int,
    val questionId: Int,
    val occurredAtEpochMillis: Long,
    val deferredUntilEpochMillis: Long,
    val durationMinutes: Int,
    val zoneId: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupDeferEvent) return false
        return cycleNumber == other.cycleNumber &&
            cyclePosition == other.cyclePosition &&
            questionId == other.questionId &&
            occurredAtEpochMillis == other.occurredAtEpochMillis &&
            deferredUntilEpochMillis == other.deferredUntilEpochMillis &&
            durationMinutes == other.durationMinutes &&
            zoneId == other.zoneId
    }

    override fun hashCode(): Int {
        var result = cycleNumber
        result = 31 * result + cyclePosition
        result = 31 * result + questionId
        result = 31 * result + occurredAtEpochMillis.hashCode()
        result = 31 * result + deferredUntilEpochMillis.hashCode()
        result = 31 * result + durationMinutes
        result = 31 * result + zoneId.hashCode()
        return result
    }

    override fun toString(): String {
        return "BackupDeferEvent(cycle=$cycleNumber/$cyclePosition, duration=$durationMinutes)"
    }
}
