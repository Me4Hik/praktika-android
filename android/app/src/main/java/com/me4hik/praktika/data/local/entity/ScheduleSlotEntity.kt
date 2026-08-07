// 04.08.2026 DB Refactoring cursor by Me4Hik START - сущность ScheduleSlot
package com.me4hik.praktika.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_slots",
    indices = [
        Index(value = ["timeOfDayMinutes"], unique = true),
    ],
)
data class ScheduleSlotEntity(
    @PrimaryKey val slotIndex: Int,
    val timeOfDayMinutes: Int,
)
// 04.08.2026 DB Refactoring cursor by Me4Hik END
