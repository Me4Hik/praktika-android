// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1 domain validator tests
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.SeedDataValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupRestoreDomainValidatorTest {
    private val validator = BackupRestoreDomainValidator()

    @Test
    fun validRichFixture_passes() {
        assertNull(validator.validateEnvelope(BackupRestoreFixtures.richEnvelope()))
    }

    @Test
    fun answeredWithoutAnswer_passes() {
        assertNull(validator.validatePayload(BackupRestoreFixtures.richPayload()))
    }

    @Test
    fun answeredWithAnswer_passes() {
        assertNull(validator.validatePayload(BackupRestoreFixtures.richPayload()))
    }

    @Test
    fun orphanAnswer_rejected() {
        val payload = payloadWith(
            answers = listOf(
                BackupAnswer(
                    cycleNumber = 9,
                    cyclePosition = 9,
                    text = "orphan",
                    createdAtEpochMillis = 1L,
                ),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.ORPHAN_ANSWER_REFERENCE,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun answerForNonAnsweredOccurrence_rejected() {
        val occurrences = listOf(
            occurrence(
                cycleNumber = 1,
                cyclePosition = 1,
                status = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            ),
        )
        val payload = payloadWith(
            occurrences = occurrences,
            answers = listOf(
                BackupAnswer(
                    cycleNumber = 1,
                    cyclePosition = 1,
                    text = "invalid",
                    createdAtEpochMillis = 1L,
                ),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.ANSWER_FOR_NON_ANSWERED_OCCURRENCE,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun duplicateOccurrenceKey_rejected() {
        val duplicate = occurrence(cycleNumber = 1, cyclePosition = 1)
        val payload = payloadWith(occurrences = listOf(duplicate, duplicate))
        assertEquals(
            BackupRestoreDomainFailureReason.DUPLICATE_OCCURRENCE_KEY,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun duplicateAnswerKey_rejected() {
        val answer = BackupAnswer(1, 1, "a", 1L)
        val payload = payloadWith(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 1),
            ),
            answers = listOf(answer, answer),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.DUPLICATE_ANSWER_KEY,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun invalidQuestionId_rejected() {
        val payload = payloadWith(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 22, questionId = 22),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.INVALID_QUESTION_ID,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun questionIdCyclePositionMismatch_rejected() {
        val payload = payloadWith(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 1, questionId = 2),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.QUESTION_MAPPING_MISMATCH,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun invalidScheduleCount_rejected() {
        val payload = payloadWith(
            scheduleSlots = listOf(BackupScheduleSlot(1, 600)),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.INVALID_SCHEDULE,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun duplicateScheduleMinutes_rejected() {
        val payload = payloadWith(
            scheduleSlots = listOf(
                BackupScheduleSlot(1, 600),
                BackupScheduleSlot(2, 600),
                BackupScheduleSlot(3, 900),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.INVALID_SCHEDULE,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun invalidScheduleSlotIndex_rejected() {
        val payload = payloadWith(
            scheduleSlots = listOf(
                BackupScheduleSlot(0, 600),
                BackupScheduleSlot(2, 900),
                BackupScheduleSlot(3, 1140),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.INVALID_SCHEDULE,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun invalidOccurrenceScheduleSlot_rejected() {
        val payload = payloadWith(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 1, scheduleSlotIndex = 4),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.INVALID_OCCURRENCE_SCHEDULE_SLOT,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun blankQuestionSnapshot_rejected() {
        val payload = payloadWith(
            occurrences = listOf(
                occurrence(cycleNumber = 1, cyclePosition = 1, snapshot = "  "),
            ),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.BLANK_QUESTION_SNAPSHOT,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun invalidZone_rejected() {
        val payload = payloadWith(
            practiceState = notStartedState(activeZoneId = "Not/A/Zone"),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.INVALID_ZONE,
            validator.validatePayload(payload),
        )
    }

    @Test
    fun startedWithoutOccurrences_rejected() {
        val payload = payloadWith(
            practiceState = BackupRestoreFixtures.richPracticeState(),
            occurrences = emptyList(),
        )
        assertEquals(
            BackupRestoreDomainFailureReason.STARTED_WITHOUT_OCCURRENCES,
            validator.validateStartedRequiresOccurrences(
                payload.practiceState,
                payload.occurrences,
            ),
        )
    }

    @Test
    fun sourceSeedPayloadSeedMismatch_rejected() {
        val envelope = BackupRestoreFixtures.envelope(
            payload = BackupRestoreFixtures.notStartedPayload(),
            seedVersion = 1,
        ).let { original ->
            com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope(
                backupSchemaVersion = original.backupSchemaVersion,
                backupSequence = original.backupSequence,
                createdAtEpochMillis = original.createdAtEpochMillis,
                sourceAppVersionCode = original.sourceAppVersionCode,
                sourceAppVersionName = original.sourceAppVersionName,
                sourceSeedVersion = 2,
                backupChecksumSha256 = original.backupChecksumSha256,
                payload = original.payload,
            )
        }
        assertEquals(
            BackupRestoreDomainFailureReason.SOURCE_SEED_PAYLOAD_MISMATCH,
            validator.validateEnvelope(envelope),
        )
    }

    private fun payloadWith(
        practiceState: BackupPracticeState = notStartedState(),
        scheduleSlots: List<BackupScheduleSlot> = BackupRestoreFixtures.customAscendingSchedule,
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

    private fun notStartedState(
        activeZoneId: String = BackupRestoreFixtures.ZONE_MOSCOW,
    ): BackupPracticeState {
        return BackupPracticeState(
            isPracticeStarted = false,
            isPaused = false,
            practiceStartedAtEpochMillis = null,
            currentCycleNumber = 0,
            nextCyclePosition = 1,
            lastProcessedAtEpochMillis = null,
            pausedAtEpochMillis = null,
            activeZoneId = activeZoneId,
            seedVersion = SeedDataValidator.EXPECTED_SEED_VERSION,
        )
    }

    private fun occurrence(
        cycleNumber: Int,
        cyclePosition: Int,
        questionId: Int = cyclePosition,
        status: QuestionOccurrenceStatus = QuestionOccurrenceStatus.ANSWERED,
        scheduleSlotIndex: Int = 1,
        snapshot: String = "snapshot",
    ): BackupOccurrence {
        return BackupOccurrence(
            questionId = questionId,
            questionTextSnapshot = snapshot,
            cycleNumber = cycleNumber,
            cyclePosition = cyclePosition,
            scheduleSlotIndex = scheduleSlotIndex,
            plannedAtEpochMillis = 1_700_000_000_000L,
            availableUntilEpochMillis = 1_700_003_600_000L,
            openedAtEpochMillis = null,
            completedAtEpochMillis = 1_700_000_100_000L,
            status = status.name,
            zoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
