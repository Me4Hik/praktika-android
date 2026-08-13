// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - export domain validator unit tests
package com.me4hik.praktika.data.backup.export

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupExportDomainValidatorTest {
    @Test
    fun validEmptyPracticeState_passes() {
        val payload = basePayload(
            practiceState = freshPracticeState(),
            occurrences = emptyList(),
            answers = emptyList(),
        )
        assertNull(BackupExportDomainValidator.validate(payload))
    }

    @Test
    fun deletedAnswerOccurrenceWithoutBackupAnswer_passes() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    status = QuestionOccurrenceStatus.ANSWERED,
                    completedAtEpochMillis = 1_000L,
                ),
            ),
            answers = emptyList(),
        )
        assertNull(BackupExportDomainValidator.validate(payload))
    }

    @Test
    fun answerForNonAnsweredOccurrence_rejected() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    status = QuestionOccurrenceStatus.AVAILABLE,
                ),
            ),
            answers = listOf(answer(cycleNumber = 1, cyclePosition = 1)),
        )
        assertEquals(
            BackupDatabaseUnsafeReason.ANSWER_FOR_NON_ANSWERED_OCCURRENCE,
            BackupExportDomainValidator.validate(payload),
        )
    }

    @Test
    fun multipleIncompleteOccurrences_rejected() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 1, status = QuestionOccurrenceStatus.SCHEDULED),
                occurrence(cycleNumber = 1, cyclePosition = 2, status = QuestionOccurrenceStatus.AVAILABLE),
            ),
        )
        assertEquals(
            BackupDatabaseUnsafeReason.MULTIPLE_INCOMPLETE_OCCURRENCES,
            BackupExportDomainValidator.validate(payload),
        )
    }

    @Test
    fun questionMappingMismatch_rejected() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    questionId = 2,
                    status = QuestionOccurrenceStatus.SCHEDULED,
                ),
            ),
        )
        assertEquals(
            BackupDatabaseUnsafeReason.QUESTION_MAPPING_MISMATCH,
            BackupExportDomainValidator.validate(payload),
        )
    }

    @Test
    fun answeredWithoutCompletedAt_rejected() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    status = QuestionOccurrenceStatus.ANSWERED,
                    completedAtEpochMillis = null,
                ),
            ),
        )
        assertEquals(
            BackupDatabaseUnsafeReason.INVALID_TERMINAL_COMPLETION,
            BackupExportDomainValidator.validate(payload),
        )
    }

    @Test
    fun blankAnswerText_rejected() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    status = QuestionOccurrenceStatus.ANSWERED,
                    completedAtEpochMillis = 1_000L,
                ),
            ),
            answers = listOf(
                BackupAnswer(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    text = "   ",
                    createdAtEpochMillis = 1_001L,
                ),
            ),
        )
        assertEquals(
            BackupDatabaseUnsafeReason.BLANK_ANSWER_TEXT,
            BackupExportDomainValidator.validate(payload),
        )
    }

    @Test
    fun duplicateOccurrenceKey_rejected() {
        val payload = basePayload(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 1, status = QuestionOccurrenceStatus.SCHEDULED),
                occurrence(cycleNumber = 1, cyclePosition = 1, status = QuestionOccurrenceStatus.MISSED_BY_TIME, completedAtEpochMillis = 1L),
            ),
        )
        assertEquals(
            BackupDatabaseUnsafeReason.DUPLICATE_OCCURRENCE_KEY,
            BackupExportDomainValidator.validate(payload),
        )
    }

    private fun basePayload(
        practiceState: BackupPracticeState = startedPracticeState(),
        scheduleSlots: List<BackupScheduleSlot> = defaultSchedule(),
        occurrences: List<BackupOccurrence> = emptyList(),
        answers: List<BackupAnswer> = emptyList(),
    ): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = practiceState,
            scheduleSlots = scheduleSlots,
            occurrences = occurrences,
            answers = answers,
        )
    }

    private fun freshPracticeState(): BackupPracticeState {
        return BackupPracticeState(
            isPracticeStarted = false,
            isPaused = false,
            practiceStartedAtEpochMillis = null,
            currentCycleNumber = 0,
            nextCyclePosition = 1,
            lastProcessedAtEpochMillis = null,
            pausedAtEpochMillis = null,
            activeZoneId = "Europe/Kiev",
            seedVersion = 1,
        )
    }

    private fun startedPracticeState(): BackupPracticeState {
        return BackupPracticeState(
            isPracticeStarted = true,
            isPaused = false,
            practiceStartedAtEpochMillis = 1_000L,
            currentCycleNumber = 1,
            nextCyclePosition = 2,
            lastProcessedAtEpochMillis = null,
            pausedAtEpochMillis = null,
            activeZoneId = "Europe/Kiev",
            seedVersion = 1,
        )
    }

    private fun defaultSchedule(): List<BackupScheduleSlot> {
        return listOf(
            BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 660),
            BackupScheduleSlot(slotIndex = 2, timeOfDayMinutes = 900),
            BackupScheduleSlot(slotIndex = 3, timeOfDayMinutes = 1140),
        )
    }

    private fun occurrence(
        cycleNumber: Int,
        cyclePosition: Int,
        questionId: Int = cyclePosition,
        status: QuestionOccurrenceStatus,
        completedAtEpochMillis: Long? = null,
    ): BackupOccurrence {
        return BackupOccurrence(
            questionId = questionId,
            questionTextSnapshot = "Question $questionId",
            cycleNumber = cycleNumber,
            cyclePosition = cyclePosition,
            scheduleSlotIndex = 1,
            plannedAtEpochMillis = 1_000L,
            availableUntilEpochMillis = 2_000L,
            openedAtEpochMillis = null,
            completedAtEpochMillis = completedAtEpochMillis,
            status = status.name,
            zoneId = "Europe/Kiev",
        )
    }

    private fun answer(cycleNumber: Int, cyclePosition: Int): BackupAnswer {
        return BackupAnswer(
            cycleNumber = cycleNumber,
            cyclePosition = cyclePosition,
            text = "answer",
            createdAtEpochMillis = 1_001L,
        )
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
