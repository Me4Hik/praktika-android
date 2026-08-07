// 04.08.2026 DB Refactoring cursor by Me4Hik START - сущность PracticeState
package com.me4hik.praktika.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_state")
data class PracticeStateEntity(
    @PrimaryKey val id: Int = 1,
    val isPracticeStarted: Boolean = false,
    val isPaused: Boolean = false,
    val practiceStartedAtEpochMillis: Long? = null,
    val currentCycleNumber: Int = 0,
    val nextCyclePosition: Int = 1,
    val lastProcessedAtEpochMillis: Long? = null,
    val pausedAtEpochMillis: Long? = null,
    val activeZoneId: String,
    val seedVersion: Int = 0,
)
// 04.08.2026 DB Refactoring cursor by Me4Hik END
