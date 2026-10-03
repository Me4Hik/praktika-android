package com.me4hik.praktika.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.me4hik.praktika.data.model.MoodLevel

@Entity(
    tableName = "mood_checkins",
    foreignKeys = [
        ForeignKey(
            entity = QuestionOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["occurrenceId"], unique = true),
        Index("questionId"),
        Index("updatedAtEpochMillis"),
    ],
)
data class MoodCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurrenceId: Long,
    val questionId: Int,
    val level: MoodLevel,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val zoneId: String,
)
