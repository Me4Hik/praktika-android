// 04.08.2026 DB Refactoring cursor by Me4Hik START - DAO для answers
// 04.08.2026 Seed Data cursor by Me4Hik START - архив читает questionTextSnapshot
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik START - единый archive Flow query
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.model.AnswerArchiveRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(answer: AnswerEntity): Long

    @Query("SELECT * FROM answers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AnswerEntity?

    @Query("SELECT * FROM answers WHERE occurrenceId = :occurrenceId LIMIT 1")
    suspend fun getByOccurrenceId(occurrenceId: Long): AnswerEntity?

    @Query("SELECT * FROM answers ORDER BY createdAtEpochMillis ASC")
    suspend fun getAllOrderedByCreatedAt(): List<AnswerEntity>

    @Query(
        """
        SELECT
            a.id AS answerId,
            a.occurrenceId AS occurrenceId,
            o.questionId AS questionId,
            o.questionTextSnapshot AS questionText,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            o.plannedAtEpochMillis AS plannedAtEpochMillis,
            a.text AS answerText,
            a.createdAtEpochMillis AS createdAtEpochMillis
        FROM answers a
        INNER JOIN question_occurrences o ON a.occurrenceId = o.id
        WHERE o.questionId = :questionId
        ORDER BY a.createdAtEpochMillis ASC, a.id ASC
        """,
    )
    suspend fun getAllForQuestion(questionId: Int): List<AnswerArchiveRow>

    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - reactive query по questionId
    @Query(
        """
        SELECT
            a.id AS answerId,
            a.occurrenceId AS occurrenceId,
            o.questionId AS questionId,
            o.questionTextSnapshot AS questionText,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            o.plannedAtEpochMillis AS plannedAtEpochMillis,
            a.text AS answerText,
            a.createdAtEpochMillis AS createdAtEpochMillis
        FROM answers a
        INNER JOIN question_occurrences o ON a.occurrenceId = o.id
        WHERE o.questionId = :questionId
        ORDER BY a.createdAtEpochMillis ASC, a.id ASC
        """,
    )
    fun observeArchiveEntriesForQuestion(questionId: Int): Flow<List<AnswerArchiveRow>>
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

    @Query(
        """
        SELECT
            a.id AS answerId,
            a.occurrenceId AS occurrenceId,
            o.questionId AS questionId,
            o.questionTextSnapshot AS questionText,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            o.plannedAtEpochMillis AS plannedAtEpochMillis,
            a.text AS answerText,
            a.createdAtEpochMillis AS createdAtEpochMillis
        FROM answers a
        INNER JOIN question_occurrences o ON a.occurrenceId = o.id
        ORDER BY a.createdAtEpochMillis DESC, a.id DESC
        """,
    )
    fun observeArchiveEntries(): Flow<List<AnswerArchiveRow>>

    // 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - range query для дня/периода
    @Query(
        """
        SELECT
            a.id AS answerId,
            a.occurrenceId AS occurrenceId,
            o.questionId AS questionId,
            o.questionTextSnapshot AS questionText,
            o.cycleNumber AS cycleNumber,
            o.cyclePosition AS cyclePosition,
            o.plannedAtEpochMillis AS plannedAtEpochMillis,
            a.text AS answerText,
            a.createdAtEpochMillis AS createdAtEpochMillis
        FROM answers a
        INNER JOIN question_occurrences o ON a.occurrenceId = o.id
        WHERE a.createdAtEpochMillis >= :startInclusiveEpochMillis
          AND a.createdAtEpochMillis < :endExclusiveEpochMillis
        ORDER BY a.createdAtEpochMillis DESC, a.id DESC
        """,
    )
    fun observeArchiveEntriesInRange(
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ): Flow<List<AnswerArchiveRow>>
    // 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C delete row-count signal
    @Query("DELETE FROM answers WHERE id = :id")
    suspend fun deleteById(id: Long): Int
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    @Query("DELETE FROM answers WHERE occurrenceId = :occurrenceId")
    suspend fun deleteByOccurrenceId(occurrenceId: Long)

    @Query("SELECT COUNT(*) FROM answers")
    suspend fun count(): Int

    @Query("DELETE FROM answers")
    suspend fun deleteAll()
}
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik END
// 04.08.2026 Seed Data cursor by Me4Hik END
// 04.08.2026 DB Refactoring cursor by Me4Hik END
