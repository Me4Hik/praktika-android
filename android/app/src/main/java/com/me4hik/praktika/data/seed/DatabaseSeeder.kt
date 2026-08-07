// 04.08.2026 Seed Data cursor by Me4Hik START - атомарная загрузка начальных данных
package com.me4hik.praktika.data.seed

import android.content.Context
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.local.dao.ScheduleSlotDao
import java.util.TimeZone
import java.util.concurrent.Callable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DatabaseSeeder(
    private val parser: SeedDataParser = SeedDataParser(),
    private val validator: SeedDataValidator = SeedDataValidator(),
) {
    private val mutex = Mutex()

    suspend fun seedFromAssets(
        context: Context,
        database: PraktikaDatabase = PraktikaDatabase.getInstance(context),
    ): SeedResult {
        val json = context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
        return seedFromJson(
            json = json,
            database = database,
            activeZoneId = TimeZone.getDefault().id,
        )
    }

    suspend fun seedFromJson(
        json: String,
        database: PraktikaDatabase,
        activeZoneId: String,
    ): SeedResult = mutex.withLock {
        val seedData = parser.parse(json)
        validator.validate(seedData)
        database.runInTransaction(
            Callable {
                runBlocking {
                    performSeedTransaction(seedData, database, activeZoneId)
                }
            },
        )
    }

    private suspend fun performSeedTransaction(
        seedData: SeedData,
        database: PraktikaDatabase,
        activeZoneId: String,
    ): SeedResult {
        val questionDao = database.questionDao()
        val scheduleSlotDao = database.scheduleSlotDao()
        val practiceStateDao = database.practiceStateDao()
        val occurrenceDao = database.questionOccurrenceDao()
        val answerDao = database.answerDao()

        val questionCount = questionDao.count()
        val slotCount = scheduleSlotDao.count()
        val state = practiceStateDao.get()
        val occurrenceCount = occurrenceDao.count()
        val answerCount = answerDao.count()

        val isFullyEmpty = questionCount == 0 &&
            slotCount == 0 &&
            state == null &&
            occurrenceCount == 0 &&
            answerCount == 0

        if (isFullyEmpty) {
            insertSeedData(seedData, activeZoneId, database)
            return SeedResult.Inserted
        }

        // 05.08.2026 Idempotent Seed Fix cursor by Me4Hik START - user content не блокирует повторный seed
        state?.let { practiceState ->
            when {
                practiceState.seedVersion > seedData.seedVersion -> return SeedResult.NewerSeedPresent

                practiceState.seedVersion < seedData.seedVersion ->
                    throw SeedMigrationRequiredException(
                        "Database seedVersion=${practiceState.seedVersion} is older than JSON seedVersion=${seedData.seedVersion}",
                    )
            }
        }

        if (matchesSeedData(seedData, database)) {
            return SeedResult.AlreadyInitialized
        }
        // 05.08.2026 Idempotent Seed Fix cursor by Me4Hik END

        throw SeedCorruptionException(buildCorruptionMessage(questionCount, slotCount, state))
    }

    private suspend fun insertSeedData(
        seedData: SeedData,
        activeZoneId: String,
        database: PraktikaDatabase,
    ) {
        database.questionDao().insertAll(
            seedData.questions.map { question ->
                QuestionEntity(
                    id = question.id,
                    cyclePosition = question.cyclePosition,
                    text = question.text,
                    isActive = question.isActive,
                )
            },
        )
        database.scheduleSlotDao().insertAll(
            seedData.defaultSlots.map { slot ->
                ScheduleSlotEntity(
                    slotIndex = slot.slotIndex,
                    timeOfDayMinutes = slot.timeOfDayMinutes,
                )
            },
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
                activeZoneId = activeZoneId,
                seedVersion = seedData.seedVersion,
            ),
        )
    }

    // 05.08.2026 Idempotent Seed Fix cursor by Me4Hik START - seed-owned vs user-mutable проверки
    private suspend fun matchesSeedData(
        seedData: SeedData,
        database: PraktikaDatabase,
    ): Boolean {
        val questionDao = database.questionDao()
        val scheduleSlotDao = database.scheduleSlotDao()
        val practiceStateDao = database.practiceStateDao()

        if (questionDao.count() != SeedDataValidator.EXPECTED_QUESTION_COUNT) {
            return false
        }
        if (!hasStructurallyValidScheduleSlots(scheduleSlotDao)) {
            return false
        }

        val state = practiceStateDao.get() ?: return false
        if (state.id != 1 || state.seedVersion != seedData.seedVersion) {
            return false
        }

        val storedQuestions = questionDao.getAllOrderedByCyclePosition()
        val expectedQuestions = seedData.questions.sortedBy { it.cyclePosition }
        if (storedQuestions.size != expectedQuestions.size) {
            return false
        }
        storedQuestions.forEachIndexed { index, stored ->
            val expected = expectedQuestions[index]
            if (
                stored.id != expected.id ||
                stored.cyclePosition != expected.cyclePosition ||
                stored.text != expected.text ||
                stored.isActive != expected.isActive
            ) {
                return false
            }
        }

        return true
    }

    private suspend fun hasStructurallyValidScheduleSlots(
        scheduleSlotDao: ScheduleSlotDao,
    ): Boolean {
        val storedSlots = scheduleSlotDao.getAllOrderedByTime()
        if (storedSlots.size != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            return false
        }

        val indices = mutableSetOf<Int>()
        val minutes = mutableSetOf<Int>()
        storedSlots.forEach { slot ->
            if (slot.slotIndex !in SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE) {
                return false
            }
            if (slot.timeOfDayMinutes !in SeedDataValidator.MINUTES_RANGE) {
                return false
            }
            if (!indices.add(slot.slotIndex)) {
                return false
            }
            if (!minutes.add(slot.timeOfDayMinutes)) {
                return false
            }
        }

        SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE.forEach { expectedIndex ->
            if (expectedIndex !in indices) {
                return false
            }
        }

        return true
    }
    // 05.08.2026 Idempotent Seed Fix cursor by Me4Hik END

    private fun buildCorruptionMessage(
        questionCount: Int,
        slotCount: Int,
        state: PracticeStateEntity?,
    ): String {
        return buildString {
            append("Incompatible seed state: ")
            append("questions=$questionCount, ")
            append("slots=$slotCount, ")
            append("practiceState=${if (state == null) "missing" else "present"}")
        }
    }

    private companion object {
        const val ASSET_FILE_NAME = "questions.json"
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END
