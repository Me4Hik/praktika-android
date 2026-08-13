// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room evidence factory / parity / leak host tests
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataContext
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceRoomStateSummaryFactoryTest {
    private val sentinelAnswer = "SENSITIVE_ANSWER_BODY_DO_NOT_EMIT"
    private val sentinelQuestion = "SENSITIVE_QUESTION_BODY_DO_NOT_EMIT"

    @Test
    fun semanticFixture_countsMatchDeletedAndStatusBuckets() {
        val payload = richPausedPayload()
        val room = DeviceAcceptanceRoomStateSummaryFactory.fromPayload(payload)
        assertTrue(room.practiceStarted)
        assertTrue(room.isPaused)
        assertEquals(1, room.currentCycleNumber)
        assertEquals(4, room.nextCyclePosition)
        assertEquals(listOf(900, 660, 1140), room.scheduleMinutes)
        assertEquals(4, room.occurrenceCount)
        assertEquals(1, room.answerCount)
        assertEquals(1, room.deletedTextCount)
        assertEquals(2, room.answeredCount)
        assertEquals(1, room.skippedCount)
        assertEquals(1, room.scheduledCount)
        assertEquals(0, room.missedCount)
        assertEquals(0, room.availableCount)
    }

    @Test
    fun availableStatus_isExposedAsAvailableCount() {
        val base = richPausedPayload()
        val payload = PraktikaBackupPayload(
            practiceState = base.practiceState,
            scheduleSlots = base.scheduleSlots,
            occurrences = base.occurrences.map { occ ->
                if (occ.cyclePosition == 4) {
                    BackupOccurrence(
                        questionId = occ.questionId,
                        questionTextSnapshot = occ.questionTextSnapshot,
                        cycleNumber = occ.cycleNumber,
                        cyclePosition = occ.cyclePosition,
                        scheduleSlotIndex = occ.scheduleSlotIndex,
                        plannedAtEpochMillis = occ.plannedAtEpochMillis,
                        availableUntilEpochMillis = occ.availableUntilEpochMillis,
                        openedAtEpochMillis = occ.openedAtEpochMillis,
                        completedAtEpochMillis = occ.completedAtEpochMillis,
                        status = QuestionOccurrenceStatus.AVAILABLE.name,
                        zoneId = occ.zoneId,
                    )
                } else {
                    occ
                }
            },
            answers = base.answers,
        )
        val room = DeviceAcceptanceRoomStateSummaryFactory.fromPayload(payload)
        assertEquals(1, room.availableCount)
        assertEquals(0, room.scheduledCount)
        assertEquals(4, room.occurrenceCount)
    }

    @Test
    fun roomVsBackupSemanticSummary_parityOnOverlappingFields() {
        val base = richPausedPayload()
        val payload = PraktikaBackupPayload(
            practiceState = base.practiceState,
            scheduleSlots = base.scheduleSlots,
            occurrences = base.occurrences,
            answers = listOf(BackupAnswer(1, 1, sentinelAnswer, 10L)),
        )
        val envelope = assemble(payload)
        val backup = DeviceAcceptanceSemanticSummaryFactory.fromEnvelope(envelope)
        val room = DeviceAcceptanceRoomStateSummaryFactory.fromPayload(payload)
        assertTrue(DeviceAcceptanceRoomStateSummaryFactory.overlapsBackupSemantics(room, backup))
    }

    @Test
    fun serializedRoomReport_omitsSentinelAnswerAndQuestionText() {
        val payload = PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = true,
                isPaused = true,
                practiceStartedAtEpochMillis = 1L,
                currentCycleNumber = 1,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = 2L,
                activeZoneId = "Europe/Moscow",
                seedVersion = 1,
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot(1, 660),
                BackupScheduleSlot(2, 900),
                BackupScheduleSlot(3, 1140),
            ),
            occurrences = listOf(
                occurrence(1, QuestionOccurrenceStatus.ANSWERED, sentinelQuestion),
            ),
            answers = listOf(BackupAnswer(1, 1, sentinelAnswer, 15L)),
        )
        val summary = DeviceAcceptanceRoomStateSummaryFactory.fromPayload(payload)
        val report = DeviceAcceptanceEvidenceReport(
            action = DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE.name,
            resultStatus = DeviceAcceptanceResultStatus.OK,
            harnessExecutedAtEpochMillis = 1L,
            slotA = null,
            slotB = null,
            latestValidSlot = null,
            latestValidSequence = null,
            latestValidSemantics = null,
            folderFingerprintSha256 = null,
            roomSummary = summary,
        )
        val json = DeviceAcceptanceReportSerializer.toJson(report)
        assertFalse(json.contains(sentinelAnswer))
        assertFalse(json.contains(sentinelQuestion))
        assertFalse(DeviceAcceptanceReportSerializer.containsForbiddenLeak(json))
        assertTrue(json.contains("\"action\": \"INSPECT_ROOM_STATE\""))
        assertTrue(json.contains("\"roomSummary\""))
        assertTrue(json.contains("\"isPaused\": true"))
    }

    private fun richPausedPayload(): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = true,
                isPaused = true,
                practiceStartedAtEpochMillis = 1L,
                currentCycleNumber = 1,
                nextCyclePosition = 4,
                lastProcessedAtEpochMillis = 1L,
                pausedAtEpochMillis = 2L,
                activeZoneId = "Europe/Moscow",
                seedVersion = 1,
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot(1, 900),
                BackupScheduleSlot(2, 660),
                BackupScheduleSlot(3, 1140),
            ),
            occurrences = listOf(
                occurrence(1, QuestionOccurrenceStatus.ANSWERED),
                occurrence(2, QuestionOccurrenceStatus.ANSWERED),
                occurrence(3, QuestionOccurrenceStatus.SKIPPED_BY_USER),
                occurrence(4, QuestionOccurrenceStatus.SCHEDULED),
            ),
            answers = listOf(BackupAnswer(1, 1, "kept", 10L)),
        )
    }

    private fun occurrence(
        position: Int,
        status: QuestionOccurrenceStatus,
        questionText: String = "q$position",
    ): BackupOccurrence {
        return BackupOccurrence(
            questionId = position,
            questionTextSnapshot = questionText,
            cycleNumber = 1,
            cyclePosition = position,
            scheduleSlotIndex = ((position - 1) % 3) + 1,
            plannedAtEpochMillis = 1L,
            availableUntilEpochMillis = 2L,
            openedAtEpochMillis = null,
            completedAtEpochMillis = null,
            status = status.name,
            zoneId = "Europe/Moscow",
        )
    }

    private fun assemble(payload: PraktikaBackupPayload): PraktikaBackupEnvelope {
        val metadata = BackupSnapshotMetadataContext(
            createdAtEpochMillis = 100L,
            sourceAppVersionCode = 7,
            sourceAppVersionName = "1.0-accelerated",
        )
        return when (
            val result = BackupEnvelopeAssembler.assemble(
                payload = payload,
                backupSequence = 2L,
                metadata = metadata,
            )
        ) {
            is BackupEnvelopeAssemblyResult.Success -> result.envelope
            else -> error("assemble failed")
        }
    }
}
// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
