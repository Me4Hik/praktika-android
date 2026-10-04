package com.me4hik.praktika.data.mood

import androidx.room.withTransaction
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.MoodCheckInEntity
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.measurement.AnalyticsTracker
import com.me4hik.praktika.measurement.NoOpAnalyticsTracker
import com.me4hik.praktika.measurement.ProductAnalyticsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MoodCheckInRepository {
    fun observeLevel(occurrenceId: Long): Flow<MoodLevel?>

    suspend fun upsertForOccurrence(
        occurrenceId: Long,
        level: MoodLevel,
    ): MoodLevel
}

class RoomMoodCheckInRepository(
    private val database: PraktikaDatabase,
    private val timeProvider: TimeProvider,
    private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
) : MoodCheckInRepository {
    override fun observeLevel(occurrenceId: Long): Flow<MoodLevel?> {
        return database.moodCheckInDao().observeByOccurrenceId(occurrenceId).map { it?.level }
    }

    override suspend fun upsertForOccurrence(
        occurrenceId: Long,
        level: MoodLevel,
    ): MoodLevel {
        val saved = database.withTransaction {
            val occurrence = database.questionOccurrenceDao().getById(occurrenceId)
                ?: throw IllegalStateException("Occurrence $occurrenceId not found for mood check-in")
            val now = timeProvider.nowEpochMillis()
            val existing = database.moodCheckInDao().getByOccurrenceId(occurrenceId)
            if (existing == null) {
                database.moodCheckInDao().insert(
                    MoodCheckInEntity(
                        occurrenceId = occurrenceId,
                        questionId = occurrence.questionId,
                        level = level,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        zoneId = occurrence.zoneId,
                    ),
                )
            } else {
                database.moodCheckInDao().update(
                    existing.copy(
                        level = level,
                        updatedAtEpochMillis = now,
                    ),
                )
            }
            level
        }
        // Fact-only product event: never attach mood level to measurement payload.
        runCatching { analyticsTracker.track(ProductAnalyticsEvents.moodCheckinSaved()) }
        return saved
    }
}
