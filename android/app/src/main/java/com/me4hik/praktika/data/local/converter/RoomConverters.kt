// 04.08.2026 DB Refactoring cursor by Me4Hik START - конвертер статусов QuestionOccurrence
package com.me4hik.praktika.data.local.converter

import androidx.room.TypeConverter
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

class RoomConverters {
    @TypeConverter
    fun fromQuestionOccurrenceStatus(status: QuestionOccurrenceStatus): String = status.name

    @TypeConverter
    fun toQuestionOccurrenceStatus(value: String): QuestionOccurrenceStatus {
        return try {
            QuestionOccurrenceStatus.valueOf(value)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown QuestionOccurrenceStatus value: $value", exception)
        }
    }

    @TypeConverter
    fun fromMoodLevel(level: MoodLevel): String = level.name

    @TypeConverter
    fun toMoodLevel(value: String): MoodLevel = MoodLevel.fromStorage(value)
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
