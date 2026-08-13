// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 3 SAF storage tests
package com.me4hik.praktika.data.backup.storage

import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostWriteEnvelopeVerifierTest {
    private val golden = BackupGoldenFixtures.goldenEnvelopeWithChecksum()

    @Test
    fun matchingIdentityPasses() {
        val expected = PostWriteEnvelopeVerifier.expectedFrom(golden)
        val result = PostWriteEnvelopeVerifier.verify(expected, golden)
        assertEquals(PostWriteEnvelopeVerifier.VerificationResult.Success, result)
    }

    @Test
    fun wrongSequenceFails() {
        val expected = PostWriteEnvelopeVerifier.expectedFrom(golden)
        val actual = copyEnvelope(golden, backupSequence = golden.backupSequence + 1)
        val result = PostWriteEnvelopeVerifier.verify(expected, actual)
        assertTrue(result is PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch)
        assertEquals(
            "backupSequence",
            (result as PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch).field,
        )
    }

    @Test
    fun wrongChecksumFails() {
        val expected = PostWriteEnvelopeVerifier.expectedFrom(golden)
        val actual = copyEnvelope(golden, backupChecksumSha256 = "0".repeat(64))
        val result = PostWriteEnvelopeVerifier.verify(expected, actual)
        assertTrue(result is PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch)
        assertEquals(
            "backupChecksumSha256",
            (result as PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch).field,
        )
    }

    @Test
    fun wrongSchemaFails() {
        val expected = PostWriteEnvelopeVerifier.expectedFrom(golden)
        val actual = copyEnvelope(golden, backupSchemaVersion = 99)
        val result = PostWriteEnvelopeVerifier.verify(expected, actual)
        assertTrue(result is PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch)
        assertEquals(
            "backupSchemaVersion",
            (result as PostWriteEnvelopeVerifier.VerificationResult.ExpectedIdentityMismatch).field,
        )
    }

    private fun copyEnvelope(
        source: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
        backupSchemaVersion: Int = source.backupSchemaVersion,
        backupSequence: Long = source.backupSequence,
        createdAtEpochMillis: Long = source.createdAtEpochMillis,
        backupChecksumSha256: String = source.backupChecksumSha256,
    ): com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope {
        return com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope(
            backupSchemaVersion = backupSchemaVersion,
            backupSequence = backupSequence,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceAppVersionCode = source.sourceAppVersionCode,
            sourceAppVersionName = source.sourceAppVersionName,
            sourceSeedVersion = source.sourceSeedVersion,
            backupChecksumSha256 = backupChecksumSha256,
            payload = source.payload,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
