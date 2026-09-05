// 04.08.2026 DB Refactoring cursor by Me4Hik START - DAO для question_occurrences
// 04.08.2026 Cycle Engine cursor by Me4Hik START - запросы для цикла
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.model.AnalyticsTerminalOccurrenceRow
import com.me4hik.praktika.data.local.model.ArchiveTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occurrence: QuestionOccurrenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(occurrences: List<QuestionOccurrenceEntity>)

    @Query("SELECT * FROM question_occurrences WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuestionOccurrenceEntity?

    @Query(
        """
        SELECT * FROM question_occurrences
        WHERE cycleNumber = :cycleNumber AND cyclePosition = :cyclePosition
        LIMIT 1
        """,
    )
    suspend fun getByCycleAndPosition(
        cycleNumber: Int,
        cyclePosition: Int,
    ): QuestionOccurrenceEntity?

    @Query(
        """
        SELECT * FROM question_occurrences
        WHERE status = 'AVAILABLE'
        ORDER BY plannedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun getAvailableOccurrence(): QuestionOccurrenceEntity?

    @Query(
        """
        SELECT * FROM question_occurrences
        WHERE status = 'SCHEDULED'
        ORDER BY plannedAtEpochMillis ASC
        LIMIT 1
        """,
    )
    suspend fun getNextScheduledOccurrence(): QuestionOccurrenceEntity?

    @Query(
        """
        SELECT * FROM question_occurrences
        WHERE questionId = :questionId
        ORDER BY plannedAtEpochMillis ASC
        """,
    )
    suspend fun getAllForQuestion(questionId: Int): List<QuestionOccurrenceEntity>

    @Query(
        """
        SELECT * FROM question_occurrences
        WHERE status IN ('SCHEDULED', 'AVAILABLE')
        ORDER BY plannedAtEpochMillis ASC
        """,
    )
    suspend fun getIncompleteOrdered(): List<QuestionOccurrenceEntity>

    // 05.08.2026 Main Screen cursor by Me4Hik START - reactive incomplete list для UI
    @Query(
        """
        SELECT *
        FROM question_occurrences
        WHERE status IN ('SCHEDULED', 'AVAILABLE')
        ORDER BY plannedAtEpochMillis ASC, id ASC
        """,
    )
    fun observeIncompleteOrdered(): Flow<List<QuestionOccurrenceEntity>>
    // 05.08.2026 Main Screen cursor by Me4Hik END

    @Query(
        """
        SELECT * FROM question_occurrences
        ORDER BY plannedAtEpochMillis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestByPlannedAt(): QuestionOccurrenceEntity?

    @Query("SELECT COUNT(*) FROM question_occurrences WHERE status = :status")
    suspend fun countByStatus(status: QuestionOccurrenceStatus): Int

    @Query("SELECT * FROM question_occurrences ORDER BY plannedAtEpochMillis ASC")
    suspend fun getAllOrderedByPlannedAt(): List<QuestionOccurrenceEntity>

    @Query(
        """
        UPDATE question_occurrences
        SET status = :newStatus, completedAtEpochMillis = :completedAtEpochMillis
        WHERE id = :id AND status = :expectedStatus
        """,
    )
    suspend fun updateStatusIfCurrent(
        id: Long,
        expectedStatus: QuestionOccurrenceStatus,
        newStatus: QuestionOccurrenceStatus,
        completedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE question_occurrences
        SET scheduleSlotIndex = :scheduleSlotIndex,
            plannedAtEpochMillis = :plannedAtEpochMillis,
            availableUntilEpochMillis = :availableUntilEpochMillis,
            zoneId = :zoneId,
            status = :status,
            completedAtEpochMillis = :completedAtEpochMillis
        WHERE id = :id AND status = 'SCHEDULED'
        """,
    )
    suspend fun rescheduleScheduledOccurrence(
        id: Long,
        scheduleSlotIndex: Int,
        plannedAtEpochMillis: Long,
        availableUntilEpochMillis: Long,
        zoneId: String,
        status: QuestionOccurrenceStatus,
        completedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE question_occurrences
        SET availableUntilEpochMillis = :availableUntilEpochMillis
        WHERE id = :id AND status = 'AVAILABLE'
        """,
    )
    suspend fun updateAvailableUntil(
        id: Long,
        availableUntilEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE question_occurrences
        SET openedAtEpochMillis = :openedAtEpochMillis
        WHERE id = :id AND openedAtEpochMillis IS NULL AND status = 'AVAILABLE'
        """,
    )
    suspend fun markOpenedIfNull(
        id: Long,
        openedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE question_occurrences
        SET deferredUntilEpochMillis = :deferredUntilEpochMillis,
            openedAtEpochMillis = NULL
        WHERE id = :id AND status = 'AVAILABLE'
        """,
    )
    suspend fun deferAvailableOccurrence(
        id: Long,
        deferredUntilEpochMillis: Long,
    ): Int

    /**
     * Consumes a matured defer promise exactly once:
     * clear deferredUntil and reset openedAt so one fresh due QUESTION can post.
     * Idempotent when deferredUntil is already null or still in the future.
     */
    @Query(
        """
        UPDATE question_occurrences
        SET deferredUntilEpochMillis = NULL,
            openedAtEpochMillis = NULL
        WHERE id = :id
          AND status = 'AVAILABLE'
          AND deferredUntilEpochMillis IS NOT NULL
          AND deferredUntilEpochMillis <= :nowEpochMillis
        """,
    )
    suspend fun consumeMaturedDeferIfDue(
        id: Long,
        nowEpochMillis: Long,
    ): Int

    @Update
    suspend fun update(occurrence: QuestionOccurrenceEntity)

    @Query(
        """
        UPDATE question_occurrences
        SET status = :status, completedAtEpochMillis = :completedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun updateStatusAndCompletion(
        id: Long,
        status: QuestionOccurrenceStatus,
        completedAtEpochMillis: Long?,
    )

    @Query("SELECT COUNT(*) FROM question_occurrences")
    suspend fun count(): Int

    @Query("DELETE FROM question_occurrences")
    suspend fun deleteAll()

    // PROMPT 115 — terminal archive history (LEFT JOIN answer for ANSWERED)
    @Query(
        """
        SELECT
            o.id AS occurrenceId,
            o.questionId AS questionId,
            o.questionTextSnapshot AS questionTextSnapshot,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            o.status AS status,
            o.completedAtEpochMillis AS completedAtEpochMillis,
            a.id AS answerId,
            a.text AS answerText,
            a.createdAtEpochMillis AS answerCreatedAtEpochMillis
        FROM question_occurrences o
        LEFT JOIN answers a ON a.occurrenceId = o.id
        WHERE o.status IN ('ANSWERED', 'SKIPPED_BY_USER', 'MISSED_BY_TIME')
          AND o.questionId = :questionId
        ORDER BY o.completedAtEpochMillis ASC, o.id ASC
        """,
    )
    fun observeTerminalArchiveRowsForQuestion(questionId: Int): Flow<List<ArchiveTerminalOccurrenceRow>>

    @Query(
        """
        SELECT
            o.id AS occurrenceId,
            o.questionId AS questionId,
            o.questionTextSnapshot AS questionTextSnapshot,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            o.status AS status,
            o.completedAtEpochMillis AS completedAtEpochMillis,
            a.id AS answerId,
            a.text AS answerText,
            a.createdAtEpochMillis AS answerCreatedAtEpochMillis
        FROM question_occurrences o
        LEFT JOIN answers a ON a.occurrenceId = o.id
        WHERE o.status IN ('ANSWERED', 'SKIPPED_BY_USER', 'MISSED_BY_TIME')
        ORDER BY o.completedAtEpochMillis ASC, o.id ASC
        """,
    )
    fun observeTerminalArchiveRows(): Flow<List<ArchiveTerminalOccurrenceRow>>

    // PROMPT 129 — analytics terminal rows (includes stored zoneId)
    @Query(
        """
        SELECT
            id AS occurrenceId,
            questionId AS questionId,
            questionTextSnapshot AS questionTextSnapshot,
            cyclePosition AS cyclePosition,
            status AS status,
            completedAtEpochMillis AS completedAtEpochMillis,
            zoneId AS zoneId
        FROM question_occurrences
        WHERE status IN ('ANSWERED', 'SKIPPED_BY_USER', 'MISSED_BY_TIME')
          AND completedAtEpochMillis IS NOT NULL
        ORDER BY completedAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun getAnalyticsTerminalRows(): List<AnalyticsTerminalOccurrenceRow>
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
// 04.08.2026 DB Refactoring cursor by Me4Hik END
