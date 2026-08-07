// 04.08.2026 DB Refactoring cursor by Me4Hik START - DAO для practice_state
package com.me4hik.praktika.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeStateDao {
    @Query("SELECT * FROM practice_state WHERE id = 1 LIMIT 1")
    suspend fun get(): PracticeStateEntity?

    // 05.08.2026 Main Screen cursor by Me4Hik START - reactive observe для UI
    @Query("SELECT * FROM practice_state WHERE id = 1")
    fun observe(): Flow<PracticeStateEntity?>
    // 05.08.2026 Main Screen cursor by Me4Hik END

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(state: PracticeStateEntity)

    @Upsert
    suspend fun upsert(state: PracticeStateEntity)

    @Update
    suspend fun update(state: PracticeStateEntity)

    @Query("DELETE FROM practice_state")
    suspend fun delete()
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
