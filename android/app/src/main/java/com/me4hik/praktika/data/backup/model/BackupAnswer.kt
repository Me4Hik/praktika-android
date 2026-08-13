// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - portable answer DTO
package com.me4hik.praktika.data.backup.model

class BackupAnswer(
    val cycleNumber: Int,
    val cyclePosition: Int,
    val text: String,
    val createdAtEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupAnswer) return false
        return cycleNumber == other.cycleNumber &&
            cyclePosition == other.cyclePosition &&
            text == other.text &&
            createdAtEpochMillis == other.createdAtEpochMillis
    }

    override fun hashCode(): Int {
        var result = cycleNumber
        result = 31 * result + cyclePosition
        result = 31 * result + text.hashCode()
        result = 31 * result + createdAtEpochMillis.hashCode()
        return result
    }

    override fun toString(): String {
        return "BackupAnswer(cycle=$cycleNumber/$cyclePosition, createdAt=$createdAtEpochMillis)"
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
