// 06.08.2026 Settings Schedule cursor by Me4Hik START - read-слой расписания для Settings
package com.me4hik.praktika.data.read

import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.seed.SeedDataValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface ScheduleReadRepository {
    fun observeSchedule(): Flow<ScheduleReadSnapshot>
}

data class ScheduleSlotReadModel(
    val slotIndex: Int,
    val timeOfDayMinutes: Int,
)

data class ScheduleReadSnapshot(
    val slots: List<ScheduleSlotReadModel>,
)

class RoomScheduleReadRepository(
    private val database: PraktikaDatabase,
) : ScheduleReadRepository {

    override fun observeSchedule(): Flow<ScheduleReadSnapshot> {
        return database.scheduleSlotDao()
            .observeAllOrderedByTime()
            .map { rows -> buildSnapshot(rows.map { ScheduleSlotReadModel(it.slotIndex, it.timeOfDayMinutes) }) }
            .distinctUntilChanged()
    }

    private fun buildSnapshot(slots: List<ScheduleSlotReadModel>): ScheduleReadSnapshot {
        if (slots.size != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            throw CycleCorruptionException(
                "Expected ${SeedDataValidator.EXPECTED_SLOT_COUNT} schedule slots, found ${slots.size}",
            )
        }

        val indices = mutableSetOf<Int>()
        val minutes = mutableSetOf<Int>()
        slots.forEach { slot ->
            if (slot.slotIndex !in SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE) {
                throw CycleCorruptionException("Invalid schedule slotIndex ${slot.slotIndex}")
            }
            if (slot.timeOfDayMinutes !in SeedDataValidator.MINUTES_RANGE) {
                throw CycleCorruptionException(
                    "Invalid schedule timeOfDayMinutes ${slot.timeOfDayMinutes}",
                )
            }
            if (!indices.add(slot.slotIndex)) {
                throw CycleCorruptionException("Duplicate schedule slotIndex ${slot.slotIndex}")
            }
            if (!minutes.add(slot.timeOfDayMinutes)) {
                throw CycleCorruptionException(
                    "Duplicate schedule timeOfDayMinutes ${slot.timeOfDayMinutes}",
                )
            }
        }

        SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE.forEach { expectedIndex ->
            if (expectedIndex !in indices) {
                throw CycleCorruptionException("Missing schedule slotIndex $expectedIndex")
            }
        }

        return ScheduleReadSnapshot(
            slots = slots.sortedBy { it.slotIndex },
        )
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
