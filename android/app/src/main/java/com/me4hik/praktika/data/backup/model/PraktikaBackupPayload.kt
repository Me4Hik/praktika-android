// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable backup payload DTO
package com.me4hik.praktika.data.backup.model

class PraktikaBackupPayload(
    val practiceState: BackupPracticeState,
    val scheduleSlots: List<BackupScheduleSlot>,
    val occurrences: List<BackupOccurrence>,
    val answers: List<BackupAnswer>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PraktikaBackupPayload) return false
        return practiceState == other.practiceState &&
            scheduleSlots == other.scheduleSlots &&
            occurrences == other.occurrences &&
            answers == other.answers
    }

    override fun hashCode(): Int {
        var result = practiceState.hashCode()
        result = 31 * result + scheduleSlots.hashCode()
        result = 31 * result + occurrences.hashCode()
        result = 31 * result + answers.hashCode()
        return result
    }

    override fun toString(): String {
        return "PraktikaBackupPayload(slots=${scheduleSlots.size}, occurrences=${occurrences.size}, answers=${answers.size})"
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
