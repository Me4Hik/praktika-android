// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - export-time domain validation
package com.me4hik.praktika.data.backup.export

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupDeferEvent
import com.me4hik.praktika.data.backup.model.BackupMoodCheckIn
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.cycle.CycleCursor
import com.me4hik.praktika.data.model.MoodLevel
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.seed.SeedDataValidator
import java.time.ZoneId

object BackupExportDomainValidator {
    private val incompleteStatuses = setOf(
        QuestionOccurrenceStatus.SCHEDULED,
        QuestionOccurrenceStatus.AVAILABLE,
    )

    fun validate(payload: PraktikaBackupPayload): BackupDatabaseUnsafeReason? {
        validatePracticeState(payload.practiceState)?.let { return it }
        validateSchedule(payload.scheduleSlots)?.let { return it }
        validateOccurrences(payload.occurrences)?.let { return it }
        validateAnswers(payload.answers, payload.occurrences)?.let { return it }
        validateDeferEvents(payload.deferEvents, payload.occurrences)?.let { return it }
        validateMoodCheckIns(payload.moodCheckIns, payload.occurrences)?.let { return it }
        return null
    }

    private fun validatePracticeState(state: BackupPracticeState): BackupDatabaseUnsafeReason? {
        if (state.seedVersion <= 0) {
            return BackupDatabaseUnsafeReason.INVALID_PRACTICE_STATE
        }
        if (state.activeZoneId.isBlank()) {
            return BackupDatabaseUnsafeReason.INVALID_PRACTICE_STATE
        }
        if (state.isPaused && state.pausedAtEpochMillis == null) {
            return BackupDatabaseUnsafeReason.INVALID_PRACTICE_STATE
        }
        if (state.isPracticeStarted && state.practiceStartedAtEpochMillis == null) {
            return BackupDatabaseUnsafeReason.INVALID_PRACTICE_STATE
        }
        if (state.isPracticeStarted) {
            if (state.currentCycleNumber < 1) {
                return BackupDatabaseUnsafeReason.INVALID_CURSOR_RANGE
            }
            if (state.nextCyclePosition !in CycleCursor.MIN_POSITION..CycleCursor.MAX_POSITION) {
                return BackupDatabaseUnsafeReason.INVALID_CURSOR_RANGE
            }
        }
        return null
    }

    private fun validateSchedule(slots: List<BackupScheduleSlot>): BackupDatabaseUnsafeReason? {
        if (slots.size != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            return BackupDatabaseUnsafeReason.INVALID_SCHEDULE
        }

        val indices = mutableSetOf<Int>()
        val minutes = mutableSetOf<Int>()
        slots.forEach { slot ->
            if (slot.slotIndex !in SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE) {
                return BackupDatabaseUnsafeReason.INVALID_SCHEDULE
            }
            if (slot.timeOfDayMinutes !in SeedDataValidator.MINUTES_RANGE) {
                return BackupDatabaseUnsafeReason.INVALID_SCHEDULE
            }
            if (!indices.add(slot.slotIndex)) {
                return BackupDatabaseUnsafeReason.INVALID_SCHEDULE
            }
            if (!minutes.add(slot.timeOfDayMinutes)) {
                return BackupDatabaseUnsafeReason.INVALID_SCHEDULE
            }
        }

        SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE.forEach { expectedIndex ->
            if (expectedIndex !in indices) {
                return BackupDatabaseUnsafeReason.INVALID_SCHEDULE
            }
        }
        return null
    }

    private fun validateOccurrences(occurrences: List<BackupOccurrence>): BackupDatabaseUnsafeReason? {
        val stableKeys = mutableSetOf<Pair<Int, Int>>()
        var incompleteCount = 0
        var availableCount = 0

        occurrences.forEach { occurrence ->
            val key = occurrence.cycleNumber to occurrence.cyclePosition
            if (!stableKeys.add(key)) {
                return BackupDatabaseUnsafeReason.DUPLICATE_OCCURRENCE_KEY
            }

            if (occurrence.questionId != occurrence.cyclePosition) {
                return BackupDatabaseUnsafeReason.QUESTION_MAPPING_MISMATCH
            }
            if (occurrence.questionId !in SeedDataValidator.EXPECTED_ID_RANGE) {
                return BackupDatabaseUnsafeReason.QUESTION_MAPPING_MISMATCH
            }
            if (occurrence.cyclePosition !in CycleCursor.MIN_POSITION..CycleCursor.MAX_POSITION) {
                return BackupDatabaseUnsafeReason.INVALID_CURSOR_RANGE
            }
            if (occurrence.cycleNumber < 1) {
                return BackupDatabaseUnsafeReason.INVALID_CURSOR_RANGE
            }
            if (occurrence.availableUntilEpochMillis < occurrence.plannedAtEpochMillis) {
                return BackupDatabaseUnsafeReason.INVALID_TIMESTAMP_RELATION
            }

            when (occurrence.status) {
                QuestionOccurrenceStatus.ANSWERED.name,
                QuestionOccurrenceStatus.MISSED_BY_TIME.name,
                QuestionOccurrenceStatus.SKIPPED_BY_USER.name,
                -> {
                    if (occurrence.completedAtEpochMillis == null) {
                        return BackupDatabaseUnsafeReason.INVALID_TERMINAL_COMPLETION
                    }
                }

                QuestionOccurrenceStatus.SCHEDULED.name,
                QuestionOccurrenceStatus.AVAILABLE.name,
                -> {
                    // Non-terminal statuses may have null completedAt.
                }

                else -> return BackupDatabaseUnsafeReason.INVALID_TERMINAL_COMPLETION
            }

            when (QuestionOccurrenceStatus.valueOf(occurrence.status)) {
                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> {
                    incompleteCount++
                    if (occurrence.status == QuestionOccurrenceStatus.AVAILABLE.name) {
                        availableCount++
                    }
                }

                else -> Unit
            }
        }

        if (incompleteCount > 1) {
            return BackupDatabaseUnsafeReason.MULTIPLE_INCOMPLETE_OCCURRENCES
        }
        if (availableCount > 1) {
            return BackupDatabaseUnsafeReason.MULTIPLE_AVAILABLE_OCCURRENCES
        }
        return null
    }

    private fun validateAnswers(
        answers: List<BackupAnswer>,
        occurrences: List<BackupOccurrence>,
    ): BackupDatabaseUnsafeReason? {
        if (answers.any { it.text.isBlank() }) {
            return BackupDatabaseUnsafeReason.BLANK_ANSWER_TEXT
        }

        val occurrenceByKey = occurrences.associateBy { it.cycleNumber to it.cyclePosition }
        answers.forEach { answer ->
            val key = answer.cycleNumber to answer.cyclePosition
            val occurrence = occurrenceByKey[key]
                ?: return BackupDatabaseUnsafeReason.ORPHAN_ANSWER_REFERENCE
            if (occurrence.status != QuestionOccurrenceStatus.ANSWERED.name) {
                return BackupDatabaseUnsafeReason.ANSWER_FOR_NON_ANSWERED_OCCURRENCE
            }
        }
        return null
    }

    private fun validateDeferEvents(
        deferEvents: List<BackupDeferEvent>,
        occurrences: List<BackupOccurrence>,
    ): BackupDatabaseUnsafeReason? {
        val occurrenceByKey = occurrences.associateBy { it.cycleNumber to it.cyclePosition }
        deferEvents.forEach { event ->
            val occurrence = occurrenceByKey[event.cycleNumber to event.cyclePosition]
                ?: return BackupDatabaseUnsafeReason.ORPHAN_DEFER_EVENT_REFERENCE
            if (event.questionId != occurrence.questionId) {
                return BackupDatabaseUnsafeReason.DEFER_QUESTION_ID_MISMATCH
            }
            if (event.durationMinutes !in DeferDurationOptions.ALLOWED_MINUTES) {
                return BackupDatabaseUnsafeReason.INVALID_DEFER_DURATION
            }
            if (event.occurredAtEpochMillis <= 0L || event.deferredUntilEpochMillis <= 0L) {
                return BackupDatabaseUnsafeReason.INVALID_DEFER_TIMESTAMP
            }
            if (event.deferredUntilEpochMillis < event.occurredAtEpochMillis) {
                return BackupDatabaseUnsafeReason.INVALID_DEFER_TIMESTAMP
            }
            if (event.zoneId.isBlank() || runCatching { ZoneId.of(event.zoneId) }.isFailure) {
                return BackupDatabaseUnsafeReason.INVALID_ZONE
            }
        }
        return null
    }

    private fun validateMoodCheckIns(
        moodCheckIns: List<BackupMoodCheckIn>,
        occurrences: List<BackupOccurrence>,
    ): BackupDatabaseUnsafeReason? {
        val occurrenceByKey = occurrences.associateBy { it.cycleNumber to it.cyclePosition }
        val seenKeys = mutableSetOf<Pair<Int, Int>>()
        moodCheckIns.forEach { event ->
            val key = event.cycleNumber to event.cyclePosition
            if (!seenKeys.add(key)) {
                return BackupDatabaseUnsafeReason.DUPLICATE_MOOD_CHECK_IN_KEY
            }
            val occurrence = occurrenceByKey[key]
                ?: return BackupDatabaseUnsafeReason.ORPHAN_MOOD_CHECK_IN_REFERENCE
            if (event.questionId != occurrence.questionId) {
                return BackupDatabaseUnsafeReason.MOOD_QUESTION_ID_MISMATCH
            }
            if (runCatching { MoodLevel.fromStorage(event.level) }.isFailure) {
                return BackupDatabaseUnsafeReason.INVALID_MOOD_LEVEL
            }
            if (event.createdAtEpochMillis <= 0L || event.updatedAtEpochMillis <= 0L) {
                return BackupDatabaseUnsafeReason.INVALID_MOOD_TIMESTAMP
            }
            if (event.updatedAtEpochMillis < event.createdAtEpochMillis) {
                return BackupDatabaseUnsafeReason.INVALID_MOOD_TIMESTAMP
            }
            if (event.zoneId.isBlank() || runCatching { ZoneId.of(event.zoneId) }.isFailure) {
                return BackupDatabaseUnsafeReason.INVALID_ZONE
            }
        }
        return null
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
