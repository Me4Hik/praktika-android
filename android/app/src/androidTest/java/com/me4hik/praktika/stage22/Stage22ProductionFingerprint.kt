// 07.08.2026 Stage 22 Stability cursor by Me4Hik START - production logical fingerprint
package com.me4hik.praktika.stage22

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

object Stage22ProductionFingerprint {
    const val STATE_FILE_NAME = "stage22_production_state.json"

    fun capture(
        database: PraktikaDatabase,
        soundEnabled: Boolean?,
        label: String,
    ): JSONObject = runBlocking {
        captureInternal(database, soundEnabled, label)
    }

    private suspend fun captureInternal(
        database: PraktikaDatabase,
        soundEnabled: Boolean?,
        label: String,
    ): JSONObject {
        val db = database.openHelper.writableDatabase
        val userVersion = db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        val quickCheck = db.query("PRAGMA quick_check").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

        val questions = database.questionDao().count()
        val scheduleSlots = database.scheduleSlotDao().getAllOrderedByTime().map { it.timeOfDayMinutes }
        val occurrences = database.questionOccurrenceDao().getAllOrderedByPlannedAt()
        val answers = database.answerDao().getAllOrderedByCreatedAt()
        val practice = database.practiceStateDao().get()

        val statusHistogram = occurrences.groupingBy { it.status.name }.eachCount()
        val answerHashes = answers.sortedWith(compareBy({ it.createdAtEpochMillis }, { it.id }))
            .map { answer ->
                answerHashLine(answer, occurrences)
            }

        return JSONObject().apply {
            put("label", label)
            put("userVersion", userVersion)
            put("quickCheck", quickCheck)
            put("questionsCount", questions)
            put("scheduleSlotMinutes", JSONArray(scheduleSlots))
            put("occurrencesCount", occurrences.size)
            put("answersCount", answers.size)
            put("occurrenceStatusHistogram", JSONObject(statusHistogram))
            put("minOccurrenceId", occurrences.minOfOrNull { it.id })
            put("maxOccurrenceId", occurrences.maxOfOrNull { it.id })
            put("minAnswerId", answers.minOfOrNull { it.id })
            put("maxAnswerId", answers.maxOfOrNull { it.id })
            put("minAnswerCreatedAt", answers.minOfOrNull { it.createdAtEpochMillis })
            put("maxAnswerCreatedAt", answers.maxOfOrNull { it.createdAtEpochMillis })
            put("practiceStarted", practice?.isPracticeStarted)
            put("practicePaused", practice?.isPaused)
            put("currentCycleNumber", practice?.currentCycleNumber)
            put("nextCyclePosition", practice?.nextCyclePosition)
            put("activeZoneId", practice?.activeZoneId)
            put("seedVersion", practice?.seedVersion)
            put("soundEnabled", soundEnabled)
            put("answerContentHashes", JSONArray(answerHashes))
            put("fingerprintHash", aggregateHash(userVersion, quickCheck, scheduleSlots, statusHistogram, answerHashes, practice, soundEnabled))
        }
    }

    fun assertEquivalent(expected: JSONObject, actual: JSONObject) {
        val keys = listOf(
            "userVersion",
            "quickCheck",
            "questionsCount",
            "answersCount",
            "occurrencesCount",
            "practiceStarted",
            "practicePaused",
            "currentCycleNumber",
            "nextCyclePosition",
            "activeZoneId",
            "seedVersion",
            "soundEnabled",
            "fingerprintHash",
        )
        keys.forEach { key ->
            val expectedValue = expected.get(key)
            val actualValue = actual.get(key)
            require(expectedValue.toString() == actualValue.toString()) {
                "Fingerprint mismatch at $key: expected=$expectedValue actual=$actualValue"
            }
        }
        require(expected.getJSONArray("scheduleSlotMinutes").toString() ==
            actual.getJSONArray("scheduleSlotMinutes").toString()) {
            "Schedule slot minutes mismatch"
        }
        require(expected.getJSONArray("answerContentHashes").toString() ==
            actual.getJSONArray("answerContentHashes").toString()) {
            "Answer content hashes mismatch"
        }
        require(expected.getJSONObject("occurrenceStatusHistogram").toString() ==
            actual.getJSONObject("occurrenceStatusHistogram").toString()) {
            "Occurrence status histogram mismatch"
        }
    }

    private fun answerHashLine(
        answer: AnswerEntity,
        occurrences: List<com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity>,
    ): JSONObject {
        val occurrence = occurrences.first { it.id == answer.occurrenceId }
        val textHash = sha256Hex(answer.text.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("answerId", answer.id)
            put("occurrenceId", answer.occurrenceId)
            put("questionId", occurrence.questionId)
            put("textLength", answer.text.length)
            put("textSha256", textHash)
            put("createdAtEpochMillis", answer.createdAtEpochMillis)
        }
    }

    private fun aggregateHash(
        userVersion: Int,
        quickCheck: String,
        scheduleSlots: List<Int>,
        statusHistogram: Map<String, Int>,
        answerHashes: List<JSONObject>,
        practice: com.me4hik.praktika.data.local.entity.PracticeStateEntity?,
        soundEnabled: Boolean?,
    ): String {
        val canonical = buildString {
            append("uv=").append(userVersion)
            append("|qc=").append(quickCheck)
            append("|slots=").append(scheduleSlots.joinToString(","))
            append("|hist=").append(statusHistogram.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" })
            append("|answers=").append(answerHashes.joinToString(";") { it.toString() })
            append("|practice=").append(practice?.isPracticeStarted)
            append("|paused=").append(practice?.isPaused)
            append("|cycle=").append(practice?.currentCycleNumber)
            append("|next=").append(practice?.nextCyclePosition)
            append("|zone=").append(practice?.activeZoneId)
            append("|sound=").append(soundEnabled)
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    suspend fun readSoundEnabled(
        repository: com.me4hik.praktika.data.preferences.SoundPreferenceRepository,
    ): Boolean = repository.soundEnabled.first()
}
// 07.08.2026 Stage 22 Stability cursor by Me4Hik END
