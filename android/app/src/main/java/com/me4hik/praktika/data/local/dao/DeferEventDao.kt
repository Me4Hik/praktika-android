// PROMPT 110 — DAO for durable defer_events
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.me4hik.praktika.data.local.entity.DeferEventEntity

@Dao
interface DeferEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: DeferEventEntity): Long

    @Query(
        """
        SELECT * FROM defer_events
        WHERE questionId = :questionId
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getForQuestionOrdered(questionId: Int): List<DeferEventEntity>

    @Query(
        """
        SELECT * FROM defer_events
        WHERE occurrenceId = :occurrenceId
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getForOccurrenceOrdered(occurrenceId: Long): List<DeferEventEntity>

    @Query(
        """
        SELECT * FROM defer_events
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAllOrdered(): List<DeferEventEntity>

    @Query("SELECT COUNT(*) FROM defer_events")
    suspend fun count(): Int

    @Query("DELETE FROM defer_events")
    suspend fun deleteAll()
}
