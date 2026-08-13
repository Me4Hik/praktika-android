// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.local.dao.ScheduleSlotDao
import com.me4hik.praktika.data.seed.SeedDataValidator

internal object ScheduleSlotRestoreWriter {
    private const val STAGED_SLOT_MINUTES_BASE = 10_000

    suspend fun applyScheduleRestore(
        slotDao: ScheduleSlotDao,
        scheduleSlots: List<BackupScheduleSlot>,
    ) {
        SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE.forEach { slotIndex ->
            slotDao.updateTime(
                slotIndex = slotIndex,
                timeOfDayMinutes = STAGED_SLOT_MINUTES_BASE + slotIndex,
            )
        }

        scheduleSlots.sortedBy { it.slotIndex }.forEach { slot ->
            slotDao.updateTime(
                slotIndex = slot.slotIndex,
                timeOfDayMinutes = slot.timeOfDayMinutes,
            )
        }
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
