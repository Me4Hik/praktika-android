// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - backup format validator JVM tests
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatValidatorTest {
    @Test
    fun validChecksum_accept() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val result = BackupFormatValidator.validate(envelope)
        assertTrue(result is BackupFormatValidationResult.Valid)
    }

    @Test
    fun checksumMismatch_reject() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val tampered = envelope.copyChecksum("0".repeat(64))
        val result = BackupFormatValidator.validate(tampered)
        assertTrue(result is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.ChecksumMismatch,
            (result as BackupFormatValidationResult.Invalid).reason,
        )
    }

    @Test
    fun checksumMalformedUppercase_reject() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val uppercase = envelope.copyChecksum(envelope.backupChecksumSha256.uppercase())
        val result = BackupFormatValidator.validate(uppercase)
        assertTrue(result is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.InvalidChecksumFormat,
            (result as BackupFormatValidationResult.Invalid).reason,
        )
    }

    @Test
    fun checksumMalformedLength_reject() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val shortChecksum = envelope.copyChecksum("abc123")
        val result = BackupFormatValidator.validate(shortChecksum)
        assertTrue(result is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.InvalidChecksumFormat,
            (result as BackupFormatValidationResult.Invalid).reason,
        )
    }

    @Test
    fun sequenceZero_invalid() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
            .copyEnvelope(backupSequence = 0L)
            .withRecalculatedChecksum()
        val result = BackupFormatValidator.validate(envelope)
        assertTrue(result is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.InvalidMetadata,
            (result as BackupFormatValidationResult.Invalid).reason,
        )
    }

    @Test
    fun sequenceNegative_invalid() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
            .copyEnvelope(backupSequence = -1L)
            .withRecalculatedChecksum()
        val result = BackupFormatValidator.validate(envelope)
        assertTrue(result is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.InvalidMetadata,
            (result as BackupFormatValidationResult.Invalid).reason,
        )
    }

    @Test
    fun sequenceMax_readValid() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
            .copyEnvelope(backupSequence = Long.MAX_VALUE)
            .withRecalculatedChecksum()
        val result = BackupFormatValidator.validate(envelope)
        assertTrue(result is BackupFormatValidationResult.Valid)
    }

    private fun PraktikaBackupEnvelope.copyChecksum(checksum: String): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = backupSchemaVersion,
            backupSequence = backupSequence,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = sourceAppVersionCode,
            sourceAppVersionName = sourceAppVersionName,
            sourceSeedVersion = sourceSeedVersion,
            backupChecksumSha256 = checksum,
            payload = payload,
        )
    }

    private fun PraktikaBackupEnvelope.copyEnvelope(backupSequence: Long): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = backupSchemaVersion,
            backupSequence = backupSequence,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = sourceAppVersionCode,
            sourceAppVersionName = sourceAppVersionName,
            sourceSeedVersion = sourceSeedVersion,
            backupChecksumSha256 = backupChecksumSha256,
            payload = payload,
        )
    }

    private fun PraktikaBackupEnvelope.withRecalculatedChecksum(): PraktikaBackupEnvelope {
        val checksum = BackupChecksum.calculate(
            copyChecksum(""),
        )
        return copyChecksum(checksum)
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
