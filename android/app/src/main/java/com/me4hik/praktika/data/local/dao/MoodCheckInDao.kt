package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.me4hik.praktika.data.local.entity.MoodCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodCheckInDao {
    @Query("SELECT * FROM mood_checkins WHERE occurrenceId = :occurrenceId LIMIT 1")
    fun observeByOccurrenceId(occurrenceId: Long): Flow<MoodCheckInEntity?>

    @Query("SELECT * FROM mood_checkins WHERE occurrenceId = :occurrenceId LIMIT 1")
    suspend fun getByOccurrenceId(occurrenceId: Long): MoodCheckInEntity?

    @Query(
        """
        SELECT * FROM mood_checkins
        ORDER BY updatedAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAllOrdered(): List<MoodCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MoodCheckInEntity): Long

    @Update
    suspend fun update(entity: MoodCheckInEntity)

    @Query("SELECT COUNT(*) FROM mood_checkins")
    suspend fun count(): Int

    @Query("DELETE FROM mood_checkins")
    suspend fun deleteAll()
}
