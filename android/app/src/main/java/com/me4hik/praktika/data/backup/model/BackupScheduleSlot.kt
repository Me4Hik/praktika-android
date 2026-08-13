// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable schedule slot DTO
package com.me4hik.praktika.data.backup.model

class BackupScheduleSlot(
    val slotIndex: Int,
    val timeOfDayMinutes: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupScheduleSlot) return false
        return slotIndex == other.slotIndex && timeOfDayMinutes == other.timeOfDayMinutes
    }

    override fun hashCode(): Int = 31 * slotIndex + timeOfDayMinutes

    override fun toString(): String = "BackupScheduleSlot(index=$slotIndex, minutes=$timeOfDayMinutes)"
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
