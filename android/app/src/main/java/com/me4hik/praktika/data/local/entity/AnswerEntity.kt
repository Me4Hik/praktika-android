// 04.08.2026 DB Refactoring cursor by Me4Hik START - сущность Answer
package com.me4hik.praktika.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "answers",
    foreignKeys = [
        ForeignKey(
            entity = QuestionOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("createdAtEpochMillis"),
        Index(value = ["occurrenceId"], unique = true),
    ],
)
data class AnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurrenceId: Long,
    val text: String,
    val createdAtEpochMillis: Long,
)
// 04.08.2026 DB Refactoring cursor by Me4Hik END
