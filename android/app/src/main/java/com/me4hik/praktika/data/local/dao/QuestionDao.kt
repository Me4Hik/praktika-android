// 04.08.2026 DB Refactoring cursor by Me4Hik START - DAO для questions
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.me4hik.praktika.data.local.entity.QuestionEntity

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Query("SELECT * FROM questions ORDER BY cyclePosition ASC")
    suspend fun getAllOrderedByCyclePosition(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): QuestionEntity?

    @Query("SELECT * FROM questions WHERE cyclePosition = :cyclePosition LIMIT 1")
    suspend fun getByCyclePosition(cyclePosition: Int): QuestionEntity?

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Query("UPDATE questions SET text = :text WHERE id = :id")
    suspend fun updateText(id: Int, text: String)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
