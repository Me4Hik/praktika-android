package com.me4hik.praktika.data.backup.model

class BackupMoodCheckIn(
    val cycleNumber: Int,
    val cyclePosition: Int,
    val questionId: Int,
    val level: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val zoneId: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupMoodCheckIn) return false
        return cycleNumber == other.cycleNumber &&
            cyclePosition == other.cyclePosition &&
            questionId == other.questionId &&
            level == other.level &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            updatedAtEpochMillis == other.updatedAtEpochMillis &&
            zoneId == other.zoneId
    }

    override fun hashCode(): Int {
        var result = cycleNumber
        result = 31 * result + cyclePosition
        result = 31 * result + questionId
        result = 31 * result + level.hashCode()
        result = 31 * result + createdAtEpochMillis.hashCode()
        result = 31 * result + updatedAtEpochMillis.hashCode()
        result = 31 * result + zoneId.hashCode()
        return result
    }

    override fun toString(): String {
        return "BackupMoodCheckIn(cycle=$cycleNumber/$cyclePosition, level=$level)"
    }
}
