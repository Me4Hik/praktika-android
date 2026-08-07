// 04.08.2026 DB Refactoring cursor by Me4Hik START - сущность Question
package com.me4hik.praktika.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["cyclePosition"], unique = true),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: Int,
    val cyclePosition: Int,
    val text: String,
    val isActive: Boolean = true,
)
// 04.08.2026 DB Refactoring cursor by Me4Hik END
