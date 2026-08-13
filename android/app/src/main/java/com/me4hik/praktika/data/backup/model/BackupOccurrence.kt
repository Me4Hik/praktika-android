// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable occurrence DTO
package com.me4hik.praktika.data.backup.model

class BackupOccurrence(
    val questionId: Int,
    val questionTextSnapshot: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val scheduleSlotIndex: Int,
    val plannedAtEpochMillis: Long,
    val availableUntilEpochMillis: Long,
    val openedAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
    val status: String,
    val zoneId: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupOccurrence) return false
        return questionId == other.questionId &&
            questionTextSnapshot == other.questionTextSnapshot &&
            cycleNumber == other.cycleNumber &&
            cyclePosition == other.cyclePosition &&
            scheduleSlotIndex == other.scheduleSlotIndex &&
            plannedAtEpochMillis == other.plannedAtEpochMillis &&
            availableUntilEpochMillis == other.availableUntilEpochMillis &&
            openedAtEpochMillis == other.openedAtEpochMillis &&
            completedAtEpochMillis == other.completedAtEpochMillis &&
            status == other.status &&
            zoneId == other.zoneId
    }

    override fun hashCode(): Int {
        var result = questionId
        result = 31 * result + questionTextSnapshot.hashCode()
        result = 31 * result + cycleNumber
        result = 31 * result + cyclePosition
        result = 31 * result + scheduleSlotIndex
        result = 31 * result + plannedAtEpochMillis.hashCode()
        result = 31 * result + availableUntilEpochMillis.hashCode()
        result = 31 * result + (openedAtEpochMillis?.hashCode() ?: 0)
        result = 31 * result + (completedAtEpochMillis?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        result = 31 * result + zoneId.hashCode()
        return result
    }

    override fun toString(): String {
        return "BackupOccurrence(cycle=$cycleNumber/$cyclePosition, questionId=$questionId, status=$status)"
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
