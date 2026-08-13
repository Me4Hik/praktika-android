// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable practice state DTO
package com.me4hik.praktika.data.backup.model

class BackupPracticeState(
    val isPracticeStarted: Boolean,
    val isPaused: Boolean,
    val practiceStartedAtEpochMillis: Long?,
    val currentCycleNumber: Int,
    val nextCyclePosition: Int,
    val lastProcessedAtEpochMillis: Long?,
    val pausedAtEpochMillis: Long?,
    val activeZoneId: String,
    val seedVersion: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupPracticeState) return false
        return isPracticeStarted == other.isPracticeStarted &&
            isPaused == other.isPaused &&
            practiceStartedAtEpochMillis == other.practiceStartedAtEpochMillis &&
            currentCycleNumber == other.currentCycleNumber &&
            nextCyclePosition == other.nextCyclePosition &&
            lastProcessedAtEpochMillis == other.lastProcessedAtEpochMillis &&
            pausedAtEpochMillis == other.pausedAtEpochMillis &&
            activeZoneId == other.activeZoneId &&
            seedVersion == other.seedVersion
    }

    override fun hashCode(): Int {
        var result = isPracticeStarted.hashCode()
        result = 31 * result + isPaused.hashCode()
        result = 31 * result + (practiceStartedAtEpochMillis?.hashCode() ?: 0)
        result = 31 * result + currentCycleNumber
        result = 31 * result + nextCyclePosition
        result = 31 * result + (lastProcessedAtEpochMillis?.hashCode() ?: 0)
        result = 31 * result + (pausedAtEpochMillis?.hashCode() ?: 0)
        result = 31 * result + activeZoneId.hashCode()
        result = 31 * result + seedVersion
        return result
    }

    override fun toString(): String {
        return "BackupPracticeState(started=$isPracticeStarted, paused=$isPaused, cycle=$currentCycleNumber/$nextCyclePosition, seed=$seedVersion)"
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
