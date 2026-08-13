// 11.08.2026 DATA VAULT Stage 1.5 cursor by Me4Hik START - Android/JVM backup format compatibility proof
package com.me4hik.praktika.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.codec.BackupJsonDecoder
import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator
import com.me4hik.praktika.data.backup.validate.BackupJsonDecodeResult
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BackupFormatAndroidCompatibilityTest {
    @Test
    fun testA_android_integrityGoldenHash_matchesJvm() {
        val hash = BackupChecksum.calculate(GoldenV1AndroidFixture.goldenEnvelopeWithoutChecksum())
        assertEquals(GoldenV1AndroidFixture.GOLDEN_INTEGRITY_HASH_V1, hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun testB_android_jsonRoundTrip_preservesGoldenHash() {
        val source = GoldenV1AndroidFixture.goldenEnvelopeWithChecksum()
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(source)
        val decoded = BackupJsonDecoder.decode(bytes)
        assertTrue(decoded is BackupJsonDecodeResult.Success)

        val envelope = (decoded as BackupJsonDecodeResult.Success).envelope
        val validation = BackupFormatValidator.validate(envelope)
        assertTrue(validation is BackupFormatValidationResult.Valid)

        val hash = BackupChecksum.calculate(envelope)
        assertEquals(GoldenV1AndroidFixture.GOLDEN_INTEGRITY_HASH_V1, hash)

        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V1, envelope.backupSchemaVersion)
        assertEquals(42L, envelope.backupSequence)
        assertEquals(3, envelope.payload.occurrences.size)
        assertEquals(1, envelope.payload.answers.size)
        assertNull(envelope.payload.practiceState.lastProcessedAtEpochMillis)
        assertNull(envelope.payload.practiceState.pausedAtEpochMillis)

        val deletedAnswerOccurrence = envelope.payload.occurrences.single {
            it.cycleNumber == 1 && it.cyclePosition == 2
        }
        assertEquals(QuestionOccurrenceStatus.ANSWERED.name, deletedAnswerOccurrence.status)
        assertEquals(1_700_000_050_000L, deletedAnswerOccurrence.completedAtEpochMillis)
        assertTrue(
            envelope.payload.answers.none {
                it.cycleNumber == deletedAnswerOccurrence.cycleNumber &&
                    it.cyclePosition == deletedAnswerOccurrence.cyclePosition
            },
        )
    }

    @Test
    fun testC_jvmGoldenJson_androidDecode_matchesGoldenHash() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bytes = context.assets.open("datavault/golden-v1-jvm.json").use { it.readBytes() }
        val decoded = BackupJsonDecoder.decode(bytes)
        assertTrue(decoded is BackupJsonDecodeResult.Success)

        val envelope = (decoded as BackupJsonDecodeResult.Success).envelope
        val validation = BackupFormatValidator.validate(envelope)
        assertTrue(validation is BackupFormatValidationResult.Valid)

        val hash = BackupChecksum.calculate(envelope)
        assertEquals(GoldenV1AndroidFixture.GOLDEN_INTEGRITY_HASH_V1, hash)
    }

    @Test
    fun testD_android_strictUnknownField_rejected() {
        val json = BackupJsonEncoder.encodeToJsonString(GoldenV1AndroidFixture.goldenEnvelopeWithChecksum())
            .replace(
                "\"backupSchemaVersion\":1",
                "\"backupSchemaVersion\":1,\"unexpectedField\":1",
            )
        val decoded = BackupJsonDecoder.decode(json.toByteArray(Charsets.UTF_8))
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.UnknownField,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun testE_android_checksumTamper_rejected() {
        val envelope = GoldenV1AndroidFixture.goldenEnvelopeWithChecksum()
        val tamperedChecksum = envelope.backupChecksumSha256.mapIndexed { index, char ->
            if (index == 0) {
                if (char == 'a') 'b' else 'a'
            } else {
                char
            }
        }.joinToString("")
        assertNotEquals(envelope.backupChecksumSha256, tamperedChecksum)

        val tampered = com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope(
            backupSchemaVersion = envelope.backupSchemaVersion,
            backupSequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceAppVersionCode = envelope.sourceAppVersionCode,
            sourceAppVersionName = envelope.sourceAppVersionName,
            sourceSeedVersion = envelope.sourceSeedVersion,
            backupChecksumSha256 = tamperedChecksum,
            payload = envelope.payload,
        )

        val validation = BackupFormatValidator.validate(tampered)
        assertTrue(validation is BackupFormatValidationResult.Invalid)
        assertEquals(
            BackupFormatFailureReason.ChecksumMismatch,
            (validation as BackupFormatValidationResult.Invalid).reason,
        )
    }
}
// 11.08.2026 DATA VAULT Stage 1.5 cursor by Me4Hik END
