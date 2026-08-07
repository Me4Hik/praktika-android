// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - large archive seed helpers
package com.me4hik.praktika.stage22

import androidx.room.withTransaction
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime

internal object Stage22ArchiveSeedSupport {
    private const val QUESTION_COUNT = 21
    private const val ZONE = "Europe/Moscow"
    private val baseEpochMillis = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneId.of(ZONE))
        .toInstant()
        .toEpochMilli()

    suspend fun seedAnsweredArchive(database: PraktikaDatabase, answerCount: Int) {
        require(answerCount > 0)
        database.withTransaction {
            seedBaseIfEmpty(database, answerCount)
            val occurrences = ArrayList<QuestionOccurrenceEntity>(answerCount)
            val answers = ArrayList<AnswerEntity>(answerCount)
            repeat(answerCount) { index ->
                val cycleNumber = index / QUESTION_COUNT + 1
                val cyclePosition = index % QUESTION_COUNT + 1
                val questionId = cyclePosition
                val createdAt = baseEpochMillis + index * 86_400_000L
                val plannedAt = createdAt - 3_600_000L
                occurrences += QuestionOccurrenceEntity(
                    questionId = questionId,
                    questionTextSnapshot = "Question snapshot $questionId",
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    scheduleSlotIndex = (index % 3) + 1,
                    plannedAtEpochMillis = plannedAt,
                    availableUntilEpochMillis = plannedAt + 14_400_000L,
                    completedAtEpochMillis = createdAt,
                    status = QuestionOccurrenceStatus.ANSWERED,
                    zoneId = ZONE,
                )
                answers += AnswerEntity(
                    occurrenceId = 0L,
                    text = "Answer #$index",
                    createdAtEpochMillis = createdAt,
                )
            }
            database.questionOccurrenceDao().insertAll(occurrences)
            val insertedOccurrences = database.questionOccurrenceDao().getAllOrderedByPlannedAt()
            val answeredOccurrences = insertedOccurrences
                .filter { it.status == QuestionOccurrenceStatus.ANSWERED }
                .sortedWith(compareBy({ it.cycleNumber }, { it.cyclePosition }))
            require(answeredOccurrences.size >= answerCount) {
                "Expected at least $answerCount answered occurrences, got ${answeredOccurrences.size}"
            }
            answeredOccurrences.take(answerCount).forEachIndexed { index, occurrence ->
                database.answerDao().insert(
                    answers[index].copy(occurrenceId = occurrence.id),
                )
            }
        }
    }

    private suspend fun seedBaseIfEmpty(database: PraktikaDatabase, answerCount: Int) {
        if (database.questionDao().count() == 0) {
            database.questionDao().insertAll(
                (1..QUESTION_COUNT).map { index ->
                    QuestionEntity(
                        id = index,
                        cyclePosition = index,
                        text = "Question $index",
                        isActive = true,
                    )
                },
            )
        }
        if (database.scheduleSlotDao().count() == 0) {
            database.scheduleSlotDao().insertAll(
                listOf(
                    ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                    ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
                    ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
                ),
            )
        }
        if (database.practiceStateDao().get() == null) {
            database.practiceStateDao().insert(
                PracticeStateEntity(
                    id = 1,
                    isPracticeStarted = true,
                    isPaused = false,
                    practiceStartedAtEpochMillis = baseEpochMillis,
                    currentCycleNumber = (answerCount / QUESTION_COUNT) + 1,
                    nextCyclePosition = 1,
                    lastProcessedAtEpochMillis = baseEpochMillis,
                    pausedAtEpochMillis = null,
                    activeZoneId = ZONE,
                    seedVersion = 1,
                ),
            )
        }
    }

}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END
