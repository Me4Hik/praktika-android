// 04.08.2026 DB Refactoring cursor by Me4Hik START - DAO для schedule_slots
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleSlotDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(slots: List<ScheduleSlotEntity>)

    @Query("SELECT * FROM schedule_slots ORDER BY timeOfDayMinutes ASC")
    suspend fun getAllOrderedByTime(): List<ScheduleSlotEntity>

    // 06.08.2026 Settings Schedule cursor by Me4Hik START - Flow для Settings read layer
    @Query("SELECT * FROM schedule_slots ORDER BY timeOfDayMinutes ASC")
    fun observeAllOrderedByTime(): Flow<List<ScheduleSlotEntity>>
    // 06.08.2026 Settings Schedule cursor by Me4Hik END

    @Query("SELECT * FROM schedule_slots WHERE slotIndex = :slotIndex LIMIT 1")
    suspend fun getByIndex(slotIndex: Int): ScheduleSlotEntity?

    @Query("UPDATE schedule_slots SET timeOfDayMinutes = :timeOfDayMinutes WHERE slotIndex = :slotIndex")
    suspend fun updateTime(slotIndex: Int, timeOfDayMinutes: Int)

    @Query("SELECT COUNT(*) FROM schedule_slots")
    suspend fun count(): Int

    @Query("DELETE FROM schedule_slots")
    suspend fun deleteAll()
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
