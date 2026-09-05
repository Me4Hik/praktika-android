// PROMPT 110 — durable defer_events append-only row
package com.me4hik.praktika.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "defer_events",
    foreignKeys = [
        ForeignKey(
            entity = QuestionOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("occurrenceId"),
        Index("questionId"),
        Index("occurredAtEpochMillis"),
        Index(value = ["questionId", "occurredAtEpochMillis"]),
    ],
)
data class DeferEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurrenceId: Long,
    val questionId: Int,
    val occurredAtEpochMillis: Long,
    val deferredUntilEpochMillis: Long,
    val durationMinutes: Int,
    val zoneId: String,
)
