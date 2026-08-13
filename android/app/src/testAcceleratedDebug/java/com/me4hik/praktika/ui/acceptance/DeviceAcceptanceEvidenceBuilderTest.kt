// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - evidence builder / SHA / semantics host tests
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAcceptanceEvidenceBuilderTest {
    private val sentinelAnswerText = "SENTINEL_SECRET_ANSWER_TEXT_SHOULD_NEVER_APPEAR"

    @Test
    fun fullFileSha_knownBytesExact_andChangesOnByteFlip() {
        val bytes = "hello-backup-bytes".toByteArray(Charsets.UTF_8)
        val sha = DeviceAcceptanceFolderFingerprint.sha256Hex(bytes)
        assertEquals(64, sha.length)
        val flipped = bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertNotEquals(sha, DeviceAcceptanceFolderFingerprint.sha256Hex(flipped))
    }

    @Test
    fun missingA_validB_selectsBAsLatest() {
        val envelope = assemble(sequence = 3L, answers = emptyList())
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        val report = DeviceAcceptanceEvidenceBuilder.buildFolderEvidence(
            action = "INSPECT_CURRENT",
            resultStatus = DeviceAcceptanceResultStatus.OK,
            harnessExecutedAtEpochMillis = 1L,
            slotABytes = DeviceAcceptanceSlotBytes.Missing,
            slotBBytes = DeviceAcceptanceSlotBytes.Readable(bytes),
        )
        assertEquals(DeviceAcceptanceDecodeStatus.MISSING, report.slotA!!.decodeStatus)
        assertEquals(DeviceAcceptanceDecodeStatus.VALID, report.slotB!!.decodeStatus)
        assertEquals(DeviceAcceptanceSlotId.B, report.latestValidSlot)
        assertEquals(3L, report.latestValidSequence)
        assertNotNull(report.folderFingerprintSha256)
    }

    @Test
    fun bothValid_selectsHigherSequence_thenTieBreakB() {
        val a = assemble(sequence = 5L, createdAt = 100L)
        val b = assemble(sequence = 5L, createdAt = 200L)
        val report = DeviceAcceptanceEvidenceBuilder.buildFolderEvidence(
            action = "INSPECT",
            resultStatus = DeviceAcceptanceResultStatus.OK,
            harnessExecutedAtEpochMillis = 1L,
            slotABytes = DeviceAcceptanceSlotBytes.Readable(BackupJsonEncoder.encodeToUtf8Bytes(a)),
            slotBBytes = DeviceAcceptanceSlotBytes.Readable(BackupJsonEncoder.encodeToUtf8Bytes(b)),
        )
        assertEquals(DeviceAcceptanceSlotId.B, report.latestValidSlot)
    }

    @Test
    fun invalidChecksum_reportsChecksumInvalid_andShaStillPresent() {
        val valid = assemble(sequence = 2L)
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(valid)
        val json = String(bytes, Charsets.UTF_8).replace(
            valid.backupChecksumSha256,
            "0".repeat(64),
        )
        val badBytes = json.toByteArray(Charsets.UTF_8)
        val summary = DeviceAcceptanceEvidenceBuilder.buildSlotSummary(
            DeviceAcceptanceSlotId.A,
            DeviceAcceptanceSlotBytes.Readable(badBytes),
        )
        assertEquals(DeviceAcceptanceDecodeStatus.CHECKSUM_INVALID, summary.decodeStatus)
        assertEquals(false, summary.checksumValid)
        assertNotNull(summary.fullFileSha256)
        assertEquals(badBytes.size.toLong(), summary.byteSize)
    }

    @Test
    fun tooLargeAndProviderUnreadable_safeCategories() {
        assertEquals(
            DeviceAcceptanceDecodeStatus.TOO_LARGE,
            DeviceAcceptanceEvidenceBuilder.buildSlotSummary(
                DeviceAcceptanceSlotId.A,
                DeviceAcceptanceSlotBytes.TooLarge,
            ).decodeStatus,
        )
        assertEquals(
            DeviceAcceptanceDecodeStatus.PROVIDER_FAILURE,
            DeviceAcceptanceEvidenceBuilder.buildSlotSummary(
                DeviceAcceptanceSlotId.B,
                DeviceAcceptanceSlotBytes.Unreadable(DeviceAcceptanceDecodeStatus.PROVIDER_FAILURE),
            ).decodeStatus,
        )
    }

    @Test
    fun semanticCounts_exact_andNoAnswerBodyInReport() {
        val payload = PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = 1L,
                currentCycleNumber = 1,
                nextCyclePosition = 2,
                lastProcessedAtEpochMillis = 1L,
                pausedAtEpochMillis = null,
                activeZoneId = "Europe/Moscow",
                seedVersion = 1,
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot(1, 600),
                BackupScheduleSlot(2, 800),
                BackupScheduleSlot(3, 1000),
            ),
            occurrences = listOf(
                occurrence(1, 1, QuestionOccurrenceStatus.ANSWERED),
                occurrence(1, 2, QuestionOccurrenceStatus.ANSWERED),
                occurrence(1, 3, QuestionOccurrenceStatus.SKIPPED_BY_USER),
                occurrence(1, 4, QuestionOccurrenceStatus.SCHEDULED),
                occurrence(1, 5, QuestionOccurrenceStatus.MISSED_BY_TIME),
            ),
            answers = listOf(
                BackupAnswer(1, 1, sentinelAnswerText, 10L),
            ),
        )
        val envelope = assemble(sequence = 7L, payload = payload)
        val report = DeviceAcceptanceEvidenceBuilder.buildFolderEvidence(
            action = "INSPECT_CURRENT",
            resultStatus = DeviceAcceptanceResultStatus.OK,
            harnessExecutedAtEpochMillis = 42L,
            slotABytes = DeviceAcceptanceSlotBytes.Readable(
                BackupJsonEncoder.encodeToUtf8Bytes(envelope),
            ),
            slotBBytes = DeviceAcceptanceSlotBytes.Missing,
        )
        val semantics = report.latestValidSemantics!!
        assertEquals(5, semantics.occurrenceCount)
        assertEquals(1, semantics.answerCount)
        assertEquals(1, semantics.deletedTextCount)
        assertEquals(2, semantics.answeredCount)
        assertEquals(1, semantics.skippedCount)
        assertEquals(1, semantics.scheduledCount)
        assertEquals(1, semantics.missedCount)
        assertEquals(listOf(600, 800, 1000), semantics.scheduleMinutes)
        assertTrue(semantics.practiceStarted)
        assertFalse(semantics.isPaused)

        val json = DeviceAcceptanceReportSerializer.toJson(report)
        assertFalse(json.contains(sentinelAnswerText))
        assertFalse(DeviceAcceptanceReportSerializer.containsForbiddenLeak(json))
        assertFalse(json.contains("content://"))
        assertFalse(json.contains("\"payload\""))
    }

    private fun occurrence(
        cycle: Int,
        position: Int,
        status: QuestionOccurrenceStatus,
    ): BackupOccurrence {
        return BackupOccurrence(
            questionId = position,
            questionTextSnapshot = "q$position",
            cycleNumber = cycle,
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

    private fun assemble(
        sequence: Long,
        createdAt: Long = 1_700_000_000_000L,
        answers: List<BackupAnswer> = emptyList(),
        payload: PraktikaBackupPayload? = null,
    ): PraktikaBackupEnvelope {
        val resolvedPayload = payload ?: PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = 1L,
                currentCycleNumber = 1,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = 1L,
                pausedAtEpochMillis = null,
                activeZoneId = "Europe/Moscow",
                seedVersion = 1,
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot(1, 600),
                BackupScheduleSlot(2, 800),
                BackupScheduleSlot(3, 1000),
            ),
            occurrences = emptyList(),
            answers = answers,
        )
        val metadata = BackupSnapshotMetadataContext(
            createdAtEpochMillis = createdAt,
            sourceAppVersionCode = 7,
            sourceAppVersionName = "1.0-accelerated",
        )
        return when (
            val assembled = BackupEnvelopeAssembler.assemble(
                payload = resolvedPayload,
                backupSequence = sequence,
                metadata = metadata,
            )
        ) {
            is BackupEnvelopeAssemblyResult.Success -> assembled.envelope
            else -> error("assemble failed")
        }
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
