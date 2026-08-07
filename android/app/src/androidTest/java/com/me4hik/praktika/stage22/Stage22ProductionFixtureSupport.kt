// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - production persistence fixture
package com.me4hik.praktika.stage22

import androidx.room.withTransaction
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntime
import java.io.File

object Stage22ProductionFixtureSupport {
    val CUSTOM_SCHEDULE = listOf(630, 860, 1210)
    const val ANSWER_ASCII = "Persistence answer one"
    const val ANSWER_CYRILLIC = "Кириллица 🎉 persistence"

    suspend fun assertFreshInstallation(runtime: PraktikaRuntime) {
        val database = runtime.database
        val answerCount = database.answerDao().count()
        val occurrenceCount = database.questionOccurrenceDao().count()
        val practice = database.practiceStateDao().get()
        require(answerCount == 0) {
            "Expected fresh production install (answers=0), found answers=$answerCount"
        }
        require(occurrenceCount == 0) {
            "Expected no occurrences before fixture, found $occurrenceCount"
        }
        require(practice != null && !practice.isPracticeStarted) {
            "Expected practice not started before fixture"
        }
    }

    suspend fun createControlledFixture(runtime: PraktikaRuntime) {
        val database = runtime.database
        val zoneId = runtime.timeProvider.currentZoneId()
        val now = runtime.timeProvider.nowEpochMillis()

        runtime.cycleRepository.updateSchedule(
            CUSTOM_SCHEDULE.mapIndexed { index, minutes ->
                ScheduleSlotUpdate(slotIndex = index + 1, timeOfDayMinutes = minutes)
            },
        )
        runtime.cycleRepository.startPractice()

        val startedOccurrence = database.questionOccurrenceDao().getIncompleteOrdered().single()
        val questionThree = database.questionDao().getByCyclePosition(3)!!

        database.withTransaction {
            val occurrenceDao = database.questionOccurrenceDao()
            val answerDao = database.answerDao()
            val practiceDao = database.practiceStateDao()

            occurrenceDao.update(
                startedOccurrence.copy(
                    status = QuestionOccurrenceStatus.ANSWERED,
                    completedAtEpochMillis = now - 4 * 3_600_000L,
                ),
            )
            answerDao.insert(
                AnswerEntity(
                    occurrenceId = startedOccurrence.id,
                    text = ANSWER_ASCII,
                    createdAtEpochMillis = now - 4 * 3_600_000L,
                ),
            )

            val skippedId = occurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = database.questionDao().getByCyclePosition(2)!!.id,
                    questionTextSnapshot = database.questionDao().getByCyclePosition(2)!!.text,
                    cycleNumber = 1,
                    cyclePosition = 2,
                    scheduleSlotIndex = 2,
                    plannedAtEpochMillis = now - 3 * 3_600_000L,
                    availableUntilEpochMillis = now - 2 * 3_600_000L,
                    completedAtEpochMillis = now - 2 * 3_600_000L,
                    status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
                    zoneId = zoneId,
                ),
            )
            check(skippedId > 0)

            val answeredThreeId = occurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = questionThree.id,
                    questionTextSnapshot = questionThree.text,
                    cycleNumber = 1,
                    cyclePosition = 3,
                    scheduleSlotIndex = 3,
                    plannedAtEpochMillis = now - 2 * 3_600_000L,
                    availableUntilEpochMillis = now - 1 * 3_600_000L,
                    completedAtEpochMillis = now - 1 * 3_600_000L,
                    status = QuestionOccurrenceStatus.ANSWERED,
                    zoneId = zoneId,
                ),
            )
            answerDao.insert(
                AnswerEntity(
                    occurrenceId = answeredThreeId,
                    text = ANSWER_CYRILLIC,
                    createdAtEpochMillis = now - 1 * 3_600_000L,
                ),
            )

            occurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = database.questionDao().getByCyclePosition(4)!!.id,
                    questionTextSnapshot = database.questionDao().getByCyclePosition(4)!!.text,
                    cycleNumber = 1,
                    cyclePosition = 4,
                    scheduleSlotIndex = 1,
                    plannedAtEpochMillis = now - 1 * 3_600_000L,
                    availableUntilEpochMillis = now - 30 * 60_000L,
                    completedAtEpochMillis = now - 30 * 60_000L,
                    status = QuestionOccurrenceStatus.MISSED_BY_TIME,
                    zoneId = zoneId,
                ),
            )

            occurrenceDao.insert(
                QuestionOccurrenceEntity(
                    questionId = database.questionDao().getByCyclePosition(5)!!.id,
                    questionTextSnapshot = database.questionDao().getByCyclePosition(5)!!.text,
                    cycleNumber = 1,
                    cyclePosition = 5,
                    scheduleSlotIndex = 2,
                    plannedAtEpochMillis = now + 2 * 3_600_000L,
                    availableUntilEpochMillis = now + 6 * 3_600_000L,
                    completedAtEpochMillis = null,
                    status = QuestionOccurrenceStatus.SCHEDULED,
                    zoneId = zoneId,
                ),
            )

            val state = practiceDao.get()!!
            practiceDao.update(
                state.copy(
                    isPracticeStarted = true,
                    isPaused = false,
                    currentCycleNumber = 1,
                    nextCyclePosition = 6,
                    lastProcessedAtEpochMillis = now,
                    pausedAtEpochMillis = null,
                    activeZoneId = zoneId,
                ),
            )
        }

        runtime.soundPreferenceRepository.setSoundEnabled(false)
        runtime.notificationCoordinator.sync(com.me4hik.praktika.notification.NotificationSyncReason.APP_START)
    }

    fun writeState(context: android.content.Context, json: org.json.JSONObject) {
        val file = File(context.filesDir, Stage22ProductionFingerprint.STATE_FILE_NAME)
        file.writeText(json.toString(2))
    }
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END
