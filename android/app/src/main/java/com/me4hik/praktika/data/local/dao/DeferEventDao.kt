// PROMPT 110 — DAO for durable defer_events
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.me4hik.praktika.data.local.entity.DeferEventEntity
import com.me4hik.praktika.data.local.model.AnalyticsDeferEventRow
import com.me4hik.praktika.data.local.model.ArchiveDeferEventRow
import kotlinx.coroutines.flow.Flow

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

    // PROMPT 115 — defer archive history rows (joined occurrence snapshot/cycle)
    // 01.10.2026 Archive T1 incomplete defer filter cursor by Me4Hik START - only terminal occurrences
    @Query(
        """
        SELECT
            d.id AS deferEventId,
            d.occurrenceId AS occurrenceId,
            d.questionId AS questionId,
            o.questionTextSnapshot AS questionTextSnapshot,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            d.occurredAtEpochMillis AS occurredAtEpochMillis,
            d.deferredUntilEpochMillis AS deferredUntilEpochMillis,
            d.durationMinutes AS durationMinutes
        FROM defer_events d
        INNER JOIN question_occurrences o ON d.occurrenceId = o.id
        WHERE d.questionId = :questionId
          AND o.status IN ('ANSWERED', 'SKIPPED_BY_USER', 'MISSED_BY_TIME')
        ORDER BY d.occurredAtEpochMillis ASC, d.id ASC
        """,
    )
    fun observeArchiveDeferRowsForQuestion(questionId: Int): Flow<List<ArchiveDeferEventRow>>

    @Query(
        """
        SELECT
            d.id AS deferEventId,
            d.occurrenceId AS occurrenceId,
            d.questionId AS questionId,
            o.questionTextSnapshot AS questionTextSnapshot,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            d.occurredAtEpochMillis AS occurredAtEpochMillis,
            d.deferredUntilEpochMillis AS deferredUntilEpochMillis,
            d.durationMinutes AS durationMinutes
        FROM defer_events d
        INNER JOIN question_occurrences o ON d.occurrenceId = o.id
        WHERE o.status IN ('ANSWERED', 'SKIPPED_BY_USER', 'MISSED_BY_TIME')
        ORDER BY d.occurredAtEpochMillis ASC, d.id ASC
        """,
    )
    fun observeArchiveDeferRows(): Flow<List<ArchiveDeferEventRow>>
    // 01.10.2026 Archive T1 incomplete defer filter cursor by Me4Hik END

    // PROMPT 129 — analytics defer rows (includes stored zoneId)
    @Query(
        """
        SELECT
            id AS deferEventId,
            occurrenceId AS occurrenceId,
            questionId AS questionId,
            occurredAtEpochMillis AS occurredAtEpochMillis,
            durationMinutes AS durationMinutes,
            zoneId AS zoneId
        FROM defer_events
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAnalyticsDeferRows(): List<AnalyticsDeferEventRow>
}
