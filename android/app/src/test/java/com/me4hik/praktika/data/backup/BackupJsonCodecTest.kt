// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - backup JSON codec JVM tests
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.codec.BackupJsonDecoder
import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.validate.BackupFormatFailureReason
import com.me4hik.praktika.data.backup.validate.BackupJsonDecodeResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun json_roundTrip() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        val decoded = BackupJsonDecoder.decode(bytes)
        assertTrue(decoded is BackupJsonDecodeResult.Success)
        assertEquals(envelope, (decoded as BackupJsonDecodeResult.Success).envelope)
    }

    @Test
    fun json_cyrillic() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
        assertTrue(json.contains("Вопрос с кириллицей"))
        assertTrue(json.contains("Ответ с emoji"))
    }

    @Test
    fun json_emoji() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
        assertTrue(json.contains("😀"))
        assertTrue(json.contains("🎯"))
    }

    @Test
    fun json_newline_quotes_backslash() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\\""))
        assertTrue(json.contains("\\\\"))
    }

    @Test
    fun json_explicitNull_roundTrip() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
        assertTrue(json.contains("\"lastProcessedAtEpochMillis\":null"))
        assertTrue(json.contains("\"pausedAtEpochMillis\":null"))
        assertTrue(json.contains("\"openedAtEpochMillis\":null"))
        val decoded = BackupJsonDecoder.decode(json.toByteArray(Charsets.UTF_8))
        assertTrue(decoded is BackupJsonDecodeResult.Success)
    }

    @Test
    fun json_missingNullable_rejected() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
            .replace("\"lastProcessedAtEpochMillis\":null,", "")
        val decoded = BackupJsonDecoder.decode(json.toByteArray(Charsets.UTF_8))
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.MissingField,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_unknownEnvelopeField_rejected() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
            .replace("\"backupSchemaVersion\":1", "\"backupSchemaVersion\":1,\"extra\":1")
        val decoded = BackupJsonDecoder.decode(json.toByteArray(Charsets.UTF_8))
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.UnknownField,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_unknownNestedField_rejected() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
            .replace("\"slotIndex\":0", "\"slotIndex\":0,\"extra\":1")
        val decoded = BackupJsonDecoder.decode(json.toByteArray(Charsets.UTF_8))
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.UnknownField,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_wrongType_rejected() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val json = BackupJsonEncoder.encodeToJsonString(envelope)
            .replace("\"currentCycleNumber\":2", "\"currentCycleNumber\":\"two\"")
        val decoded = BackupJsonDecoder.decode(json.toByteArray(Charsets.UTF_8))
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.WrongType,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_bom_rejected() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        val withBom = BackupConstants.BOM_BYTES + bytes
        val decoded = BackupJsonDecoder.decode(withBom)
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.BomNotAllowed,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_tooLarge_rejectedBeforeParse() {
        val oversized = ByteArray(BackupConstants.MAX_BACKUP_BYTES + 1) { ' '.code.toByte() }
        val decoded = BackupJsonDecoder.decode(oversized)
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.TooLarge,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_truncated_rejected() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        val truncated = bytes.copyOf(bytes.size / 2)
        val decoded = BackupJsonDecoder.decode(truncated)
        assertTrue(decoded is BackupJsonDecodeResult.Failure)
        assertEquals(
            BackupFormatFailureReason.MalformedJson,
            (decoded as BackupJsonDecodeResult.Failure).reason,
        )
    }

    @Test
    fun json_sameDto_sameBytes() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        val first = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        val second = BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        assertArrayEquals(first, second)
    }

    @Test
    fun json_doesNotMutateCallerLists() {
        val payload = BackupGoldenFixtures.goldenPayloadUnsorted
        val slotsBefore = payload.scheduleSlots.toList()
        val occurrencesBefore = payload.occurrences.toList()
        val answersBefore = payload.answers.toList()
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithChecksum()
        BackupJsonEncoder.encodeToUtf8Bytes(envelope)
        assertEquals(slotsBefore, payload.scheduleSlots)
        assertEquals(occurrencesBefore, payload.occurrences)
        assertEquals(answersBefore, payload.answers)
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
