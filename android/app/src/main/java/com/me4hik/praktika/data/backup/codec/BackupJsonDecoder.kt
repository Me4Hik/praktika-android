// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - strict portable JSON backup decoder
package com.me4hik.praktika.data.backup.codec

import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupJsonDecodeResult
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object BackupJsonDecoder {
    private var decodeFailed = false
    private val ENVELOPE_KEYS = setOf(
        "backupSchemaVersion",
        "backupSequence",
        "createdAtEpochMillis",
        "sourceAppVersionCode",
        "sourceAppVersionName",
        "sourceSeedVersion",
        "backupChecksumSha256",
        "payload",
    )

    private val PAYLOAD_KEYS = setOf(
        "practiceState",
        "scheduleSlots",
        "occurrences",
        "answers",
    )

    private val PRACTICE_STATE_KEYS = setOf(
        "isPracticeStarted",
        "isPaused",
        "practiceStartedAtEpochMillis",
        "currentCycleNumber",
        "nextCyclePosition",
        "lastProcessedAtEpochMillis",
        "pausedAtEpochMillis",
        "activeZoneId",
        "seedVersion",
    )

    private val SCHEDULE_SLOT_KEYS = setOf(
        "slotIndex",
        "timeOfDayMinutes",
    )

    private val OCCURRENCE_KEYS_REQUIRED = setOf(
        "questionId",
        "questionTextSnapshot",
        "cycleNumber",
        "cyclePosition",
        "scheduleSlotIndex",
        "plannedAtEpochMillis",
        "availableUntilEpochMillis",
        "openedAtEpochMillis",
        "completedAtEpochMillis",
        "status",
        "zoneId",
    )

    private val OCCURRENCE_KEYS_ALLOWED = OCCURRENCE_KEYS_REQUIRED + "deferredUntilEpochMillis"

    private val ANSWER_KEYS = setOf(
        "cycleNumber",
        "cyclePosition",
        "text",
        "createdAtEpochMillis",
    )

    private val ALLOWED_STATUS_NAMES = QuestionOccurrenceStatus.entries
        .map { it.name }
        .toSet()

    fun decode(bytes: ByteArray): BackupJsonDecodeResult {
        decodeFailed = false
        if (bytes.size > BackupConstants.MAX_BACKUP_BYTES) {
            return failure(BackupFormatFailureReason.TooLarge, "Backup exceeds max size")
        }

        if (bytes.size >= 3 &&
            bytes[0] == BackupConstants.BOM_BYTES[0] &&
            bytes[1] == BackupConstants.BOM_BYTES[1] &&
            bytes[2] == BackupConstants.BOM_BYTES[2]
        ) {
            return failure(BackupFormatFailureReason.BomNotAllowed, "UTF-8 BOM is not allowed")
        }

        val jsonText = try {
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            return failure(BackupFormatFailureReason.MalformedJson, "Invalid UTF-8")
        }

        val root = try {
            JSONObject(jsonText)
        } catch (_: JSONException) {
            return failure(BackupFormatFailureReason.MalformedJson, "Malformed JSON")
        }

        keyFailure(root, ENVELOPE_KEYS, "envelope")?.let { return it }

        val backupSchemaVersion = readInt(root, "backupSchemaVersion", "envelope") ?: return latestFailure()
        val backupSequence = readLong(root, "backupSequence", "envelope") ?: return latestFailure()
        val createdAtEpochMillis = readLong(root, "createdAtEpochMillis", "envelope") ?: return latestFailure()
        val sourceAppVersionCode = readInt(root, "sourceAppVersionCode", "envelope") ?: return latestFailure()
        val sourceAppVersionName = readNonNullString(root, "sourceAppVersionName", "envelope") ?: return latestFailure()
        val sourceSeedVersion = readInt(root, "sourceSeedVersion", "envelope") ?: return latestFailure()
        val backupChecksumSha256 = readNonNullString(root, "backupChecksumSha256", "envelope") ?: return latestFailure()

        val payloadObject = readObject(root, "payload", "envelope") ?: return latestFailure()
        keyFailure(payloadObject, PAYLOAD_KEYS, "payload")?.let { return it }

        val practiceStateObject = readObject(payloadObject, "practiceState", "payload") ?: return latestFailure()
        val practiceState = decodePracticeState(practiceStateObject) ?: return latestFailure()

        val scheduleSlotsArray = readArray(payloadObject, "scheduleSlots", "payload") ?: return latestFailure()
        val scheduleSlots = decodeScheduleSlots(scheduleSlotsArray) ?: return latestFailure()

        val occurrencesArray = readArray(payloadObject, "occurrences", "payload") ?: return latestFailure()
        val occurrences = decodeOccurrences(occurrencesArray) ?: return latestFailure()

        val answersArray = readArray(payloadObject, "answers", "payload") ?: return latestFailure()
        val answers = decodeAnswers(answersArray) ?: return latestFailure()

        val envelope = PraktikaBackupEnvelope(
            backupSchemaVersion = backupSchemaVersion,
            backupSequence = backupSequence,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = sourceAppVersionCode,
            sourceAppVersionName = sourceAppVersionName,
            sourceSeedVersion = sourceSeedVersion,
            backupChecksumSha256 = backupChecksumSha256,
            payload = PraktikaBackupPayload(
                practiceState = practiceState,
                scheduleSlots = scheduleSlots,
                occurrences = occurrences,
                answers = answers,
            ),
        )

        return BackupJsonDecodeResult.Success(envelope)
    }

    private fun decodePracticeState(json: JSONObject): BackupPracticeState? {
        keyFailure(json, PRACTICE_STATE_KEYS, "practiceState")?.let { return null }

        val isPracticeStarted = readBoolean(json, "isPracticeStarted", "practiceState") ?: return null
        val isPaused = readBoolean(json, "isPaused", "practiceState") ?: return null
        val practiceStartedAtEpochMillis = readNullableLong(json, "practiceStartedAtEpochMillis", "practiceState")
        if (decodeFailed) return null
        val currentCycleNumber = readInt(json, "currentCycleNumber", "practiceState") ?: return null
        val nextCyclePosition = readInt(json, "nextCyclePosition", "practiceState") ?: return null
        val lastProcessedAtEpochMillis = readNullableLong(json, "lastProcessedAtEpochMillis", "practiceState")
        if (decodeFailed) return null
        val pausedAtEpochMillis = readNullableLong(json, "pausedAtEpochMillis", "practiceState")
        if (decodeFailed) return null
        val activeZoneId = readNonNullString(json, "activeZoneId", "practiceState") ?: return null
        val seedVersion = readInt(json, "seedVersion", "practiceState") ?: return null

        return BackupPracticeState(
            isPracticeStarted = isPracticeStarted,
            isPaused = isPaused,
            practiceStartedAtEpochMillis = practiceStartedAtEpochMillis,
            currentCycleNumber = currentCycleNumber,
            nextCyclePosition = nextCyclePosition,
            lastProcessedAtEpochMillis = lastProcessedAtEpochMillis,
            pausedAtEpochMillis = pausedAtEpochMillis,
            activeZoneId = activeZoneId,
            seedVersion = seedVersion,
        )
    }

    private fun decodeScheduleSlots(array: JSONArray): List<BackupScheduleSlot>? {
        val result = mutableListOf<BackupScheduleSlot>()
        for (index in 0 until array.length()) {
            val item = readArrayObject(array, index, "scheduleSlots[$index]") ?: return null
            keyFailure(item, SCHEDULE_SLOT_KEYS, "scheduleSlots[$index]")?.let { return null }
            val slotIndex = readInt(item, "slotIndex", "scheduleSlots[$index]") ?: return null
            val timeOfDayMinutes = readInt(item, "timeOfDayMinutes", "scheduleSlots[$index]") ?: return null
            result += BackupScheduleSlot(slotIndex, timeOfDayMinutes)
        }
        return result
    }

    private fun decodeOccurrences(array: JSONArray): List<BackupOccurrence>? {
        val result = mutableListOf<BackupOccurrence>()
        for (index in 0 until array.length()) {
            val item = readArrayObject(array, index, "occurrences[$index]") ?: return null
            keyFailure(
                item,
                requiredKeys = OCCURRENCE_KEYS_REQUIRED,
                allowedKeys = OCCURRENCE_KEYS_ALLOWED,
                path = "occurrences[$index]",
            )?.let { return null }
            val questionId = readInt(item, "questionId", "occurrences[$index]") ?: return null
            val questionTextSnapshot = readNonNullString(item, "questionTextSnapshot", "occurrences[$index]") ?: return null
            val cycleNumber = readInt(item, "cycleNumber", "occurrences[$index]") ?: return null
            val cyclePosition = readInt(item, "cyclePosition", "occurrences[$index]") ?: return null
            val scheduleSlotIndex = readInt(item, "scheduleSlotIndex", "occurrences[$index]") ?: return null
            val plannedAtEpochMillis = readLong(item, "plannedAtEpochMillis", "occurrences[$index]") ?: return null
            val availableUntilEpochMillis = readLong(item, "availableUntilEpochMillis", "occurrences[$index]") ?: return null
            val openedAtEpochMillis = readNullableLong(item, "openedAtEpochMillis", "occurrences[$index]")
            if (decodeFailed) return null
            val completedAtEpochMillis = readNullableLong(item, "completedAtEpochMillis", "occurrences[$index]")
            if (decodeFailed) return null
            val deferredUntilEpochMillis = if (item.has("deferredUntilEpochMillis")) {
                readNullableLong(item, "deferredUntilEpochMillis", "occurrences[$index]")
            } else {
                null
            }
            if (decodeFailed) return null
            val status = readNonNullString(item, "status", "occurrences[$index]") ?: return null
            if (status !in ALLOWED_STATUS_NAMES) {
                setFailure(BackupFormatFailureReason.InvalidOccurrenceStatus, "Invalid occurrence status at occurrences[$index]")
                return null
            }
            val zoneId = readNonNullString(item, "zoneId", "occurrences[$index]") ?: return null
            result += BackupOccurrence(
                questionId = questionId,
                questionTextSnapshot = questionTextSnapshot,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = scheduleSlotIndex,
                plannedAtEpochMillis = plannedAtEpochMillis,
                availableUntilEpochMillis = availableUntilEpochMillis,
                openedAtEpochMillis = openedAtEpochMillis,
                completedAtEpochMillis = completedAtEpochMillis,
                deferredUntilEpochMillis = deferredUntilEpochMillis,
                status = status,
                zoneId = zoneId,
            )
        }
        return result
    }

    private fun decodeAnswers(array: JSONArray): List<BackupAnswer>? {
        val result = mutableListOf<BackupAnswer>()
        for (index in 0 until array.length()) {
            val item = readArrayObject(array, index, "answers[$index]") ?: return null
            keyFailure(item, ANSWER_KEYS, "answers[$index]")?.let { return null }
            val cycleNumber = readInt(item, "cycleNumber", "answers[$index]") ?: return null
            val cyclePosition = readInt(item, "cyclePosition", "answers[$index]") ?: return null
            val text = readNonNullString(item, "text", "answers[$index]") ?: return null
            val createdAtEpochMillis = readLong(item, "createdAtEpochMillis", "answers[$index]") ?: return null
            result += BackupAnswer(cycleNumber, cyclePosition, text, createdAtEpochMillis)
        }
        return result
    }

    private var lastReason: BackupFormatFailureReason = BackupFormatFailureReason.MalformedJson
    private var lastDetail: String = "Invalid backup format"

    private fun setFailure(reason: BackupFormatFailureReason, detail: String) {
        decodeFailed = true
        lastReason = reason
        lastDetail = detail
    }

    private fun latestFailure(): BackupJsonDecodeResult.Failure {
        return BackupJsonDecodeResult.Failure(lastReason, lastDetail)
    }

    private fun failure(reason: BackupFormatFailureReason, detail: String): BackupJsonDecodeResult.Failure {
        setFailure(reason, detail)
        return latestFailure()
    }

    private fun keyFailure(
        json: JSONObject,
        expectedKeys: Set<String>,
        path: String,
    ): BackupJsonDecodeResult.Failure? {
        return keyFailure(
            json = json,
            requiredKeys = expectedKeys,
            allowedKeys = expectedKeys,
            path = path,
        )
    }

    private fun keyFailure(
        json: JSONObject,
        requiredKeys: Set<String>,
        allowedKeys: Set<String>,
        path: String,
    ): BackupJsonDecodeResult.Failure? {
        val actualKeys = json.keys().asSequence().toSet()
        val unknown = actualKeys - allowedKeys
        if (unknown.isNotEmpty()) {
            return failure(BackupFormatFailureReason.UnknownField, "Unknown field at $path")
        }
        val missing = requiredKeys - actualKeys
        if (missing.isNotEmpty()) {
            return failure(BackupFormatFailureReason.MissingField, "Missing field at $path")
        }
        return null
    }

    private fun readObject(json: JSONObject, key: String, path: String): JSONObject? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            setFailure(BackupFormatFailureReason.NullNotAllowed, "Null not allowed for $key at $path")
            return null
        }
        return try {
            json.getJSONObject(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }

    private fun readArray(json: JSONObject, key: String, path: String): JSONArray? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            setFailure(BackupFormatFailureReason.NullNotAllowed, "Null not allowed for $key at $path")
            return null
        }
        return try {
            json.getJSONArray(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }

    private fun readArrayObject(array: JSONArray, index: Int, path: String): JSONObject? {
        return try {
            array.getJSONObject(index)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type at $path")
            null
        }
    }

    private fun readInt(json: JSONObject, key: String, path: String): Int? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            setFailure(BackupFormatFailureReason.NullNotAllowed, "Null not allowed for $key at $path")
            return null
        }
        return try {
            json.getInt(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }

    private fun readLong(json: JSONObject, key: String, path: String): Long? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            setFailure(BackupFormatFailureReason.NullNotAllowed, "Null not allowed for $key at $path")
            return null
        }
        return try {
            json.getLong(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }

    private fun readNullableLong(json: JSONObject, key: String, path: String): Long? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            return null
        }
        return try {
            json.getLong(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }

    private fun readBoolean(json: JSONObject, key: String, path: String): Boolean? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            setFailure(BackupFormatFailureReason.NullNotAllowed, "Null not allowed for $key at $path")
            return null
        }
        return try {
            json.getBoolean(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }

    private fun readNonNullString(json: JSONObject, key: String, path: String): String? {
        if (!json.has(key)) {
            setFailure(BackupFormatFailureReason.MissingField, "Missing field $key at $path")
            return null
        }
        if (json.isNull(key)) {
            setFailure(BackupFormatFailureReason.NullNotAllowed, "Null not allowed for $key at $path")
            return null
        }
        return try {
            json.getString(key)
        } catch (_: JSONException) {
            setFailure(BackupFormatFailureReason.WrongType, "Wrong type for $key at $path")
            null
        }
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
