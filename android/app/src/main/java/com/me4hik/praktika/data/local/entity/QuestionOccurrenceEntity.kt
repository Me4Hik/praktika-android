// 04.08.2026 DB Refactoring cursor by Me4Hik START - сущность QuestionOccurrence
// 04.08.2026 Seed Data cursor by Me4Hik START - questionTextSnapshot для архива
package com.me4hik.praktika.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

@Entity(
    tableName = "question_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ScheduleSlotEntity::class,
            parentColumns = ["slotIndex"],
            childColumns = ["scheduleSlotIndex"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("questionId"),
        Index("scheduleSlotIndex"),
        Index("status"),
        Index("plannedAtEpochMillis"),
        Index(value = ["cycleNumber", "cyclePosition"], unique = true),
    ],
)
data class QuestionOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val questionId: Int,
    @ColumnInfo(defaultValue = "''")
    val questionTextSnapshot: String,
    val cycleNumber: Int,
    val cyclePosition: Int,
    val scheduleSlotIndex: Int,
    val plannedAtEpochMillis: Long,
    val availableUntilEpochMillis: Long,
    val openedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val deferredUntilEpochMillis: Long? = null,
    val status: QuestionOccurrenceStatus,
    val zoneId: String,
)
// 04.08.2026 Seed Data cursor by Me4Hik END
// 04.08.2026 DB Refactoring cursor by Me4Hik END
