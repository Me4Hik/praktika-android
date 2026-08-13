// 10.08.2026 Post-release fixes cursor by Me4Hik START - vc6 production cursor desync fixture
package com.me4hik.praktika.data.cycle

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.dao.AnswerDao
import com.me4hik.praktika.data.local.dao.PracticeStateDao
import com.me4hik.praktika.data.local.dao.QuestionOccurrenceDao
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Synthetic DB matching production vc6 crash fingerprint:
 * - practiceState.nextCyclePosition = 6
 * - occurrence (cycle=1, position=6) already exists
 * - optional stale incomplete at earlier position
 */
object Vc6CursorDesyncFixtureSupport {
    const val ZONE_KIEV = "Europe/Kiev"
    const val DESYNC_CYCLE = 1
    const val DESYNC_CURSOR_POSITION = 6
    const val STALE_INCOMPLETE_POSITION = 5

    suspend fun seedBaseQuestionsAndSlots(database: PraktikaDatabase) {
        database.questionDao().insertAll(
            (1..21).map { index ->
                QuestionEntity(
                    id = index,
                    cyclePosition = index,
                    text = "Fixture question $index",
                    isActive = true,
                )
            },
        )
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(slotIndex = 1, timeOfDayMinutes = 660),
                ScheduleSlotEntity(slotIndex = 2, timeOfDayMinutes = 900),
                ScheduleSlotEntity(slotIndex = 3, timeOfDayMinutes = 1140),
            ),
        )
        database.practiceStateDao().insert(
            PracticeStateEntity(
                id = 1,
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = ZONE_KIEV,
                seedVersion = 1,
            ),
        )
    }

    suspend fun installProductionVc6DesyncFingerprint(database: PraktikaDatabase) {
        installDesyncScenario(
            database = database,
            cursorPosition = DESYNC_CURSOR_POSITION,
            existingAtCursorStatus = QuestionOccurrenceStatus.ANSWERED,
            includeStaleIncompleteAt5 = true,
            withAnswerAtCursor = true,
        )
    }

    suspend fun installDesyncScenario(
        database: PraktikaDatabase,
        cursorPosition: Int = DESYNC_CURSOR_POSITION,
        existingAtCursorStatus: QuestionOccurrenceStatus,
        includeStaleIncompleteAt5: Boolean = false,
        withAnswerAtCursor: Boolean = false,
        includeGapAt7: Boolean = false,
        extraTerminalAfterCursor: Int = 0,
    ) {
        val now = epochAt(2026, 8, 10, 17, 0, 0)
        val zoneId = ZONE_KIEV
        val occurrenceDao = database.questionOccurrenceDao()
        val answerDao = database.answerDao()
        val practiceDao = database.practiceStateDao()

        for (position in 1 until cursorPosition) {
            if (includeStaleIncompleteAt5 && position == STALE_INCOMPLETE_POSITION) {
                continue
            }
            insertTerminal(
                database = database,
                occurrenceDao = occurrenceDao,
                answerDao = answerDao,
                position = position,
                status = when (position % 3) {
                    0 -> QuestionOccurrenceStatus.MISSED_BY_TIME
                    1 -> QuestionOccurrenceStatus.ANSWERED
                    else -> QuestionOccurrenceStatus.SKIPPED_BY_USER
                },
                plannedAt = now - (cursorPosition - position + 2) * 3_600_000L,
                availableUntil = now - (cursorPosition - position + 1) * 3_600_000L,
                completedAt = now - (cursorPosition - position + 1) * 3_600_000L,
                withAnswer = position % 3 == 1,
                zoneId = zoneId,
            )
        }

        if (includeStaleIncompleteAt5) {
            val question = database.questionDao().getByCyclePosition(STALE_INCOMPLETE_POSITION)!!
            occurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = question.id,
                    questionTextSnapshot = question.text,
                    cycleNumber = DESYNC_CYCLE,
                    cyclePosition = STALE_INCOMPLETE_POSITION,
                    scheduleSlotIndex = 2,
                    plannedAtEpochMillis = now - 90 * 60_000L,
                    availableUntilEpochMillis = now - 30 * 60_000L,
                    openedAtEpochMillis = now - 80 * 60_000L,
                    completedAtEpochMillis = null,
                    status = QuestionOccurrenceStatus.AVAILABLE,
                    zoneId = zoneId,
                ),
            )
        }

        insertTerminal(
            database = database,
            occurrenceDao = occurrenceDao,
            answerDao = answerDao,
            position = cursorPosition,
            status = existingAtCursorStatus,
            plannedAt = now - 60 * 60_000L,
            availableUntil = when (existingAtCursorStatus) {
                QuestionOccurrenceStatus.SCHEDULED -> now + 2 * 3_600_000L
                QuestionOccurrenceStatus.AVAILABLE -> {
                    if (includeStaleIncompleteAt5) {
                        now - 15 * 60_000L
                    } else {
                        now + 2 * 3_600_000L
                    }
                }
                else -> now - 15 * 60_000L
            },
            completedAt = when (existingAtCursorStatus) {
                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> 0L
                else -> now - 20 * 60_000L
            },
            withAnswer = withAnswerAtCursor && existingAtCursorStatus == QuestionOccurrenceStatus.ANSWERED,
            zoneId = zoneId,
            openedAt = if (existingAtCursorStatus == QuestionOccurrenceStatus.AVAILABLE) {
                now - 50 * 60_000L
            } else {
                null
            },
        )

        if (includeGapAt7) {
            insertTerminal(
                database = database,
                occurrenceDao = occurrenceDao,
                answerDao = answerDao,
                position = 8,
                status = QuestionOccurrenceStatus.ANSWERED,
                plannedAt = now + 3_600_000L,
                availableUntil = now + 5 * 3_600_000L,
                completedAt = now + 4 * 3_600_000L,
                withAnswer = true,
                zoneId = zoneId,
            )
        }

        repeat(extraTerminalAfterCursor) { offset ->
            val position = cursorPosition + offset + 1
            if (includeGapAt7 && position == 7) {
                return@repeat
            }
            insertTerminal(
                database = database,
                occurrenceDao = occurrenceDao,
                answerDao = answerDao,
                position = position,
                status = QuestionOccurrenceStatus.MISSED_BY_TIME,
                plannedAt = now + offset * 3_600_000L,
                availableUntil = now + (offset + 1) * 3_600_000L,
                completedAt = now + (offset + 1) * 3_600_000L,
                withAnswer = false,
                zoneId = zoneId,
            )
        }

        practiceDao.update(
            practiceDao.get()!!.copy(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = now - 6 * 3_600_000L,
                currentCycleNumber = DESYNC_CYCLE,
                nextCyclePosition = cursorPosition,
                lastProcessedAtEpochMillis = now - 45 * 60_000L,
                pausedAtEpochMillis = null,
                activeZoneId = zoneId,
            ),
        )
    }

    suspend fun installTwoIncompleteConflict(database: PraktikaDatabase) {
        val now = epochAt(2026, 8, 10, 17, 0, 0)
        val zoneId = ZONE_KIEV
        val occurrenceDao = database.questionOccurrenceDao()
        val practiceDao = database.practiceStateDao()

        for (position in 1..4) {
            insertTerminal(
                database = database,
                occurrenceDao = occurrenceDao,
                answerDao = database.answerDao(),
                position = position,
                status = QuestionOccurrenceStatus.ANSWERED,
                plannedAt = now - (5 - position) * 3_600_000L,
                availableUntil = now - (4 - position) * 3_600_000L,
                completedAt = now - (4 - position) * 3_600_000L,
                withAnswer = true,
                zoneId = zoneId,
            )
        }

        val q5 = database.questionDao().getByCyclePosition(5)!!
        occurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = q5.id,
                questionTextSnapshot = q5.text,
                cycleNumber = 1,
                cyclePosition = 5,
                scheduleSlotIndex = 2,
                plannedAtEpochMillis = now - 60 * 60_000L,
                availableUntilEpochMillis = now + 60 * 60_000L,
                status = QuestionOccurrenceStatus.AVAILABLE,
                zoneId = zoneId,
            ),
        )
        val q6 = database.questionDao().getByCyclePosition(6)!!
        occurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = q6.id,
                questionTextSnapshot = q6.text,
                cycleNumber = 1,
                cyclePosition = 6,
                scheduleSlotIndex = 3,
                plannedAtEpochMillis = now + 60 * 60_000L,
                availableUntilEpochMillis = now + 3 * 60 * 60_000L,
                status = QuestionOccurrenceStatus.SCHEDULED,
                zoneId = zoneId,
            ),
        )

        practiceDao.update(
            practiceDao.get()!!.copy(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = now - 6 * 3_600_000L,
                currentCycleNumber = 1,
                nextCyclePosition = 6,
                activeZoneId = zoneId,
            ),
        )
    }

    suspend fun installQuestionMappingMismatch(database: PraktikaDatabase) {
        val now = epochAt(2026, 8, 10, 17, 0, 0)
        val zoneId = ZONE_KIEV
        val occurrenceDao = database.questionOccurrenceDao()
        val practiceDao = database.practiceStateDao()

        for (position in 1..5) {
            insertTerminal(
                database = database,
                occurrenceDao = occurrenceDao,
                answerDao = database.answerDao(),
                position = position,
                status = QuestionOccurrenceStatus.ANSWERED,
                plannedAt = now - (6 - position) * 3_600_000L,
                availableUntil = now - (5 - position) * 3_600_000L,
                completedAt = now - (5 - position) * 3_600_000L,
                withAnswer = false,
                zoneId = zoneId,
            )
        }

        val wrongQuestion = database.questionDao().getByCyclePosition(7)!!
        occurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = wrongQuestion.id,
                questionTextSnapshot = wrongQuestion.text,
                cycleNumber = 1,
                cyclePosition = 6,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = now - 60 * 60_000L,
                availableUntilEpochMillis = now - 15 * 60_000L,
                completedAtEpochMillis = now - 20 * 60_000L,
                status = QuestionOccurrenceStatus.ANSWERED,
                zoneId = zoneId,
            ),
        )

        practiceDao.update(
            practiceDao.get()!!.copy(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = now - 6 * 3_600_000L,
                currentCycleNumber = 1,
                nextCyclePosition = 6,
                activeZoneId = zoneId,
            ),
        )
    }

    private suspend fun insertTerminal(
        database: PraktikaDatabase,
        occurrenceDao: QuestionOccurrenceDao,
        answerDao: AnswerDao,
        position: Int,
        status: QuestionOccurrenceStatus,
        plannedAt: Long,
        availableUntil: Long,
        completedAt: Long,
        withAnswer: Boolean,
        zoneId: String,
        openedAt: Long? = if (status == QuestionOccurrenceStatus.ANSWERED) plannedAt else null,
    ) {
        val question = database.questionDao().getByCyclePosition(position)!!
        val id = occurrenceDao.insert(
            QuestionOccurrenceEntity(
                questionId = question.id,
                questionTextSnapshot = question.text,
                cycleNumber = DESYNC_CYCLE,
                cyclePosition = position,
                scheduleSlotIndex = ((position - 1) % 3) + 1,
                plannedAtEpochMillis = plannedAt,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = openedAt,
                completedAtEpochMillis = if (status == QuestionOccurrenceStatus.SCHEDULED ||
                    status == QuestionOccurrenceStatus.AVAILABLE
                ) {
                    null
                } else {
                    completedAt
                },
                status = status,
                zoneId = zoneId,
            ),
        )
        if (withAnswer) {
            answerDao.insert(
                AnswerEntity(
                    occurrenceId = id,
                    text = "Fixture answer at position $position",
                    createdAtEpochMillis = completedAt,
                ),
            )
        }
    }

    fun reconcileNowEpochMillis(): Long = epochAt(2026, 8, 10, 17, 0, 0)

    private fun epochAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
