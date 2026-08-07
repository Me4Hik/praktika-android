// 04.08.2026 Seed Data cursor by Me4Hik START - модели начальных данных
package com.me4hik.praktika.data.seed

data class SeedData(
    val seedVersion: Int,
    val questions: List<SeedQuestion>,
    val defaultSlots: List<SeedScheduleSlot>,
)

data class SeedQuestion(
    val id: Int,
    val cyclePosition: Int,
    val text: String,
    val isActive: Boolean,
)

data class SeedScheduleSlot(
    val slotIndex: Int,
    val timeOfDayMinutes: Int,
)

sealed class SeedResult {
    data object Inserted : SeedResult()

    data object AlreadyInitialized : SeedResult()

    data object NewerSeedPresent : SeedResult()
}
// 04.08.2026 Seed Data cursor by Me4Hik END
