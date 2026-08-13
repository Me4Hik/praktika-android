// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.cycle.CycleCursor
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.SeedDataValidator
import java.time.ZoneId

class BackupRestoreDomainValidator {
    fun validateEnvelope(envelope: PraktikaBackupEnvelope): BackupRestoreDomainFailureReason? {
        if (envelope.backupSchemaVersion != BackupConstants.BACKUP_SCHEMA_VERSION_V1) {
            return BackupRestoreDomainFailureReason.UNSUPPORTED_SCHEMA
        }
        if (envelope.sourceSeedVersion != envelope.payload.practiceState.seedVersion) {
            return BackupRestoreDomainFailureReason.SOURCE_SEED_PAYLOAD_MISMATCH
        }
        return validatePayload(envelope.payload)
    }

    fun validatePayload(payload: PraktikaBackupPayload): BackupRestoreDomainFailureReason? {
        validatePracticeState(payload.practiceState)?.let { return it }
        validateSchedule(payload.scheduleSlots)?.let { return it }
        validateOccurrences(payload.occurrences)?.let { return it }
        validateAnswers(payload.answers, payload.occurrences)?.let { return it }
        return null
    }

    private fun validatePracticeState(state: BackupPracticeState): BackupRestoreDomainFailureReason? {
        if (state.seedVersion <= 0) {
            return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
        }
        if (!isValidZoneId(state.activeZoneId)) {
            return BackupRestoreDomainFailureReason.INVALID_ZONE
        }
        if (state.isPaused && state.pausedAtEpochMillis == null) {
            return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
        }
        if (!state.isPracticeStarted) {
            if (state.currentCycleNumber != 0) {
                return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
            }
            if (state.nextCyclePosition != 1) {
                return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
            }
            if (state.practiceStartedAtEpochMillis != null) {
                return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
            }
            if (state.isPaused) {
                return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
            }
            return null
        }

        if (state.practiceStartedAtEpochMillis == null) {
            return BackupRestoreDomainFailureReason.INVALID_PRACTICE_STATE
        }
        if (state.currentCycleNumber < 1) {
            return BackupRestoreDomainFailureReason.INVALID_CURSOR_RANGE
        }
        if (state.nextCyclePosition !in CycleCursor.MIN_POSITION..CycleCursor.MAX_POSITION) {
            return BackupRestoreDomainFailureReason.INVALID_CURSOR_RANGE
        }
        return null
    }

    fun validateStartedRequiresOccurrences(
        state: BackupPracticeState,
        occurrences: List<BackupOccurrence>,
    ): BackupRestoreDomainFailureReason? {
        if (state.isPracticeStarted && occurrences.isEmpty()) {
            return BackupRestoreDomainFailureReason.STARTED_WITHOUT_OCCURRENCES
        }
        return null
    }

    private fun validateSchedule(slots: List<BackupScheduleSlot>): BackupRestoreDomainFailureReason? {
        if (slots.size != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            return BackupRestoreDomainFailureReason.INVALID_SCHEDULE
        }

        val indices = mutableSetOf<Int>()
        val minutes = mutableSetOf<Int>()
        slots.forEach { slot ->
            if (slot.slotIndex !in SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE) {
                return BackupRestoreDomainFailureReason.INVALID_SCHEDULE
            }
            if (slot.timeOfDayMinutes !in SeedDataValidator.MINUTES_RANGE) {
                return BackupRestoreDomainFailureReason.INVALID_SCHEDULE
            }
            if (!indices.add(slot.slotIndex)) {
                return BackupRestoreDomainFailureReason.INVALID_SCHEDULE
            }
            if (!minutes.add(slot.timeOfDayMinutes)) {
                return BackupRestoreDomainFailureReason.INVALID_SCHEDULE
            }
        }

        SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE.forEach { expectedIndex ->
            if (expectedIndex !in indices) {
                return BackupRestoreDomainFailureReason.INVALID_SCHEDULE
            }
        }
        return null
    }

    private fun validateOccurrences(occurrences: List<BackupOccurrence>): BackupRestoreDomainFailureReason? {
        val stableKeys = mutableSetOf<Pair<Int, Int>>()
        var incompleteCount = 0
        var availableCount = 0

        occurrences.forEach { occurrence ->
            val key = occurrence.cycleNumber to occurrence.cyclePosition
            if (!stableKeys.add(key)) {
                return BackupRestoreDomainFailureReason.DUPLICATE_OCCURRENCE_KEY
            }

            if (occurrence.questionId != occurrence.cyclePosition) {
                return BackupRestoreDomainFailureReason.QUESTION_MAPPING_MISMATCH
            }
            if (occurrence.questionId !in SeedDataValidator.EXPECTED_ID_RANGE) {
                return BackupRestoreDomainFailureReason.INVALID_QUESTION_ID
            }
            if (occurrence.cyclePosition !in CycleCursor.MIN_POSITION..CycleCursor.MAX_POSITION) {
                return BackupRestoreDomainFailureReason.INVALID_CURSOR_RANGE
            }
            if (occurrence.cycleNumber < 1) {
                return BackupRestoreDomainFailureReason.INVALID_CURSOR_RANGE
            }
            if (occurrence.scheduleSlotIndex !in SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE) {
                return BackupRestoreDomainFailureReason.INVALID_OCCURRENCE_SCHEDULE_SLOT
            }
            if (occurrence.questionTextSnapshot.isBlank()) {
                return BackupRestoreDomainFailureReason.BLANK_QUESTION_SNAPSHOT
            }
            if (!isValidZoneId(occurrence.zoneId)) {
                return BackupRestoreDomainFailureReason.INVALID_ZONE
            }
            if (occurrence.availableUntilEpochMillis < occurrence.plannedAtEpochMillis) {
                return BackupRestoreDomainFailureReason.INVALID_TIMESTAMP_RELATION
            }

            val status = parseStatus(occurrence.status) ?: return BackupRestoreDomainFailureReason.INVALID_STATUS

            when (status) {
                QuestionOccurrenceStatus.ANSWERED,
                QuestionOccurrenceStatus.MISSED_BY_TIME,
                QuestionOccurrenceStatus.SKIPPED_BY_USER,
                -> {
                    if (occurrence.completedAtEpochMillis == null) {
                        return BackupRestoreDomainFailureReason.INVALID_TERMINAL_COMPLETION
                    }
                }

                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> Unit
            }

            when (status) {
                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> {
                    incompleteCount++
                    if (status == QuestionOccurrenceStatus.AVAILABLE) {
                        availableCount++
                    }
                }

                else -> Unit
            }
        }

        if (incompleteCount > 1) {
            return BackupRestoreDomainFailureReason.MULTIPLE_INCOMPLETE_OCCURRENCES
        }
        if (availableCount > 1) {
            return BackupRestoreDomainFailureReason.MULTIPLE_AVAILABLE_OCCURRENCES
        }
        return null
    }

    private fun validateAnswers(
        answers: List<BackupAnswer>,
        occurrences: List<BackupOccurrence>,
    ): BackupRestoreDomainFailureReason? {
        val answerKeys = mutableSetOf<Pair<Int, Int>>()
        answers.forEach { answer ->
            val key = answer.cycleNumber to answer.cyclePosition
            if (!answerKeys.add(key)) {
                return BackupRestoreDomainFailureReason.DUPLICATE_ANSWER_KEY
            }
            if (answer.text.isBlank()) {
                return BackupRestoreDomainFailureReason.BLANK_ANSWER_TEXT
            }
        }

        val occurrenceByKey = occurrences.associateBy { it.cycleNumber to it.cyclePosition }
        answers.forEach { answer ->
            val key = answer.cycleNumber to answer.cyclePosition
            val occurrence = occurrenceByKey[key]
                ?: return BackupRestoreDomainFailureReason.ORPHAN_ANSWER_REFERENCE
            if (occurrence.status != QuestionOccurrenceStatus.ANSWERED.name) {
                return BackupRestoreDomainFailureReason.ANSWER_FOR_NON_ANSWERED_OCCURRENCE
            }
        }
        return null
    }

    private fun parseStatus(raw: String): QuestionOccurrenceStatus? {
        return runCatching { QuestionOccurrenceStatus.valueOf(raw) }.getOrNull()
    }

    private fun isValidZoneId(zoneId: String): Boolean {
        if (zoneId.isBlank()) {
            return false
        }
        return runCatching { ZoneId.of(zoneId) }.isSuccess
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
