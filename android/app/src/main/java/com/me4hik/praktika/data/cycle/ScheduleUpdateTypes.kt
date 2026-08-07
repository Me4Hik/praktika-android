// 06.08.2026 Settings Schedule cursor by Me4Hik START - типы updateSchedule
package com.me4hik.praktika.data.cycle

enum class ScheduleValidationReason {
    INVALID_SLOT_COUNT,
    INVALID_SLOT_INDEX,
    DUPLICATE_SLOT_INDEX,
    TIME_OUT_OF_RANGE,
    DUPLICATE_TIME,
}

class ScheduleValidationException(
    val reason: ScheduleValidationReason,
    message: String = reason.name,
) : IllegalArgumentException(message)

data class ScheduleSlotUpdate(
    val slotIndex: Int,
    val timeOfDayMinutes: Int,
)

sealed interface ScheduleUpdateResult {
    data object Success : ScheduleUpdateResult
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
