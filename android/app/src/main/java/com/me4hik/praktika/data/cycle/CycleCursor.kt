// 04.08.2026 Cycle Engine cursor by Me4Hik START - курсор позиции цикла
package com.me4hik.praktika.data.cycle

data class CycleCursor(
    val cycleNumber: Int,
    val cyclePosition: Int,
) {
    init {
        require(cycleNumber >= 1) { "cycleNumber must be >= 1, was $cycleNumber" }
        require(cyclePosition in MIN_POSITION..MAX_POSITION) {
            "cyclePosition must be in $MIN_POSITION..$MAX_POSITION, was $cyclePosition"
        }
    }

    fun advance(): CycleCursor {
        return if (cyclePosition == MAX_POSITION) {
            CycleCursor(cycleNumber = cycleNumber + 1, cyclePosition = MIN_POSITION)
        } else {
            CycleCursor(cycleNumber = cycleNumber, cyclePosition = cyclePosition + 1)
        }
    }

    companion object {
        const val MIN_POSITION = 1
        const val MAX_POSITION = 21
    }
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
