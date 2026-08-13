// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - backup slot selector JVM tests
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.slot.BackupSlotCandidate
import com.me4hik.praktika.data.backup.slot.BackupSlotSelector
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSlotSelectorTest {
    @Test
    fun A_valid_B_missing() {
        val candidate = validCandidate(BackupSlotId.A, sequence = 5L, createdAt = 100L)
        assertEquals(BackupSlotId.A, BackupSlotSelector.selectBest(listOf(candidate)))
    }

    @Test
    fun A_missing_B_valid() {
        val candidate = validCandidate(BackupSlotId.B, sequence = 5L, createdAt = 100L)
        assertEquals(BackupSlotId.B, BackupSlotSelector.selectBest(listOf(candidate)))
    }

    @Test
    fun A_invalid_B_valid() {
        val invalid = BackupSlotCandidate(
            slotId = BackupSlotId.A,
            validation = BackupFormatValidationResult.Invalid(
                BackupFormatFailureReason.ChecksumMismatch,
                "Checksum mismatch",
            ),
        )
        val valid = validCandidate(BackupSlotId.B, sequence = 1L, createdAt = 1L)
        assertEquals(BackupSlotId.B, BackupSlotSelector.selectBest(listOf(invalid, valid)))
    }

    @Test
    fun A_valid_B_invalid() {
        val valid = validCandidate(BackupSlotId.A, sequence = 1L, createdAt = 1L)
        val invalid = BackupSlotCandidate(
            slotId = BackupSlotId.B,
            validation = BackupFormatValidationResult.Invalid(
                BackupFormatFailureReason.ChecksumMismatch,
                "Checksum mismatch",
            ),
        )
        assertEquals(BackupSlotId.A, BackupSlotSelector.selectBest(listOf(valid, invalid)))
    }

    @Test
    fun bothInvalid_none() {
        val invalidA = BackupSlotCandidate(
            slotId = BackupSlotId.A,
            validation = BackupFormatValidationResult.Invalid(
                BackupFormatFailureReason.ChecksumMismatch,
                "Checksum mismatch",
            ),
        )
        val invalidB = BackupSlotCandidate(
            slotId = BackupSlotId.B,
            validation = BackupFormatValidationResult.Invalid(
                BackupFormatFailureReason.ChecksumMismatch,
                "Checksum mismatch",
            ),
        )
        assertNull(BackupSlotSelector.selectBest(listOf(invalidA, invalidB)))
    }

    @Test
    fun higherSequenceWins() {
        val a = validCandidate(BackupSlotId.A, sequence = 3L, createdAt = 100L)
        val b = validCandidate(BackupSlotId.B, sequence = 5L, createdAt = 50L)
        assertEquals(BackupSlotId.B, BackupSlotSelector.selectBest(listOf(a, b)))
    }

    @Test
    fun sameSequence_newerCreatedAtWins() {
        val a = validCandidate(BackupSlotId.A, sequence = 5L, createdAt = 100L)
        val b = validCandidate(BackupSlotId.B, sequence = 5L, createdAt = 200L)
        assertEquals(BackupSlotId.B, BackupSlotSelector.selectBest(listOf(a, b)))
    }

    @Test
    fun sameSequenceSameTime_BWins() {
        val a = validCandidate(BackupSlotId.A, sequence = 5L, createdAt = 100L)
        val b = validCandidate(BackupSlotId.B, sequence = 5L, createdAt = 100L)
        assertEquals(BackupSlotId.B, BackupSlotSelector.selectBest(listOf(a, b)))
    }

    private fun validCandidate(
        slotId: BackupSlotId,
        sequence: Long,
        createdAt: Long,
    ): BackupSlotCandidate {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val envelope = PraktikaBackupEnvelope(
            backupSchemaVersion = base.backupSchemaVersion,
            backupSequence = sequence,
            createdAtEpochMillis = createdAt,
            sourceAppVersionCode = base.sourceAppVersionCode,
            sourceAppVersionName = base.sourceAppVersionName,
            sourceSeedVersion = base.sourceSeedVersion,
            backupChecksumSha256 = "",
            payload = base.payload,
        )
        val checksum = BackupChecksum.calculate(envelope)
        val withChecksum = PraktikaBackupEnvelope(
            backupSchemaVersion = envelope.backupSchemaVersion,
            backupSequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceAppVersionCode = envelope.sourceAppVersionCode,
            sourceAppVersionName = envelope.sourceAppVersionName,
            sourceSeedVersion = envelope.sourceSeedVersion,
            backupChecksumSha256 = checksum,
            payload = envelope.payload,
        )
        return BackupSlotCandidate(
            slotId = slotId,
            validation = BackupFormatValidationResult.Valid(withChecksum),
        )
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
