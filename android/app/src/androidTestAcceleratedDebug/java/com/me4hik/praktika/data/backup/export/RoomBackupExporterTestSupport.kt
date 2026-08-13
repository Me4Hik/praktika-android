// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - accelerated exporter test support
package com.me4hik.praktika.data.backup.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.time.ZoneId
import java.time.ZonedDateTime

internal object RoomBackupExporterTestSupport {
    const val ZONE_KIEV = "Europe/Kiev"

    fun createInMemoryDatabase(): PraktikaDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
    }

    suspend fun seedBaseData(database: PraktikaDatabase) {
        val questions = (1..21).map { index ->
            QuestionEntity(
                id = index,
                cyclePosition = index,
                text = "Question $index",
                isActive = true,
            )
        }
        database.questionDao().insertAll(questions)
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

    suspend fun insertOccurrence(
        database: PraktikaDatabase,
        cycleNumber: Int,
        cyclePosition: Int,
        status: QuestionOccurrenceStatus,
        questionId: Int = cyclePosition,
        completedAtEpochMillis: Long? = null,
    ): Long {
        return database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = questionId,
                questionTextSnapshot = "Question $questionId",
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = epochAt(2026, 8, 4, 11, 0),
                availableUntilEpochMillis = epochAt(2026, 8, 4, 14, 0),
                openedAtEpochMillis = null,
                completedAtEpochMillis = completedAtEpochMillis,
                status = status,
                zoneId = ZONE_KIEV,
            ),
        )
    }

    suspend fun insertAnswer(
        database: PraktikaDatabase,
        occurrenceId: Long,
        text: String = "answer",
        createdAtEpochMillis: Long = epochAt(2026, 8, 4, 11, 30),
    ): Long {
        return database.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = text,
                createdAtEpochMillis = createdAtEpochMillis,
            ),
        )
    }

    fun epochAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        nano: Int = 0,
    ): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, nano, ZoneId.of(ZONE_KIEV))
            .toInstant()
            .toEpochMilli()
    }

    fun assertSuccess(result: BackupExportResult): BackupExportResult.Success {
        check(result is BackupExportResult.Success) {
            "Expected Success, got $result"
        }
        return result
    }

    fun assertUnsafe(result: BackupExportResult, reason: BackupDatabaseUnsafeReason) {
        check(result is BackupExportResult.DatabaseUnsafe && result.reason == reason) {
            "Expected DatabaseUnsafe($reason), got $result"
        }
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
