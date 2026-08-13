// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik START - backup integrity encoder JVM tests
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.integrity.BackupIntegrityEncoderV1
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupIntegrityEncoderTest {
    @Test
    fun golden_hash_v1() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val hash = BackupChecksum.calculate(envelope)
        assertEquals(64, hash.length)
        assertEquals(BackupGoldenFixtures.GOLDEN_INTEGRITY_HASH_V1, hash)
    }

    @Test
    fun sameDto_sameHash() {
        val first = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val second = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        assertEquals(BackupChecksum.calculate(first), BackupChecksum.calculate(second))
    }

    @Test
    fun differentAnswerText_differentHash() {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val modified = base.withPayload(
            base.payload.withAnswers(
                listOf(
                    BackupAnswer(
                        cycleNumber = 2,
                        cyclePosition = 1,
                        text = "Different answer text",
                        createdAtEpochMillis = 1_700_000_210_000L,
                    ),
                ),
            ),
        )
        assertNotEquals(BackupChecksum.calculate(base), BackupChecksum.calculate(modified))
    }

    @Test
    fun differentSequence_differentHash() {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val modified = base.copyEnvelope(backupSequence = 43L)
        assertNotEquals(BackupChecksum.calculate(base), BackupChecksum.calculate(modified))
    }

    @Test
    fun differentCreatedAt_differentHash() {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val modified = base.copyEnvelope(createdAtEpochMillis = 1_700_000_301_000L)
        assertNotEquals(BackupChecksum.calculate(base), BackupChecksum.calculate(modified))
    }

    @Test
    fun differentSeedVersion_differentHash() {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val modified = base.copyEnvelope(sourceSeedVersion = 2)
        assertNotEquals(BackupChecksum.calculate(base), BackupChecksum.calculate(modified))
    }

    @Test
    fun arrayInputOrder_sameHash() {
        val base = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val reorderedPayload = PraktikaBackupPayload(
            practiceState = base.payload.practiceState,
            scheduleSlots = base.payload.scheduleSlots.reversed(),
            occurrences = base.payload.occurrences.reversed(),
            answers = base.payload.answers.reversed(),
        )
        val reordered = base.withPayload(reorderedPayload)
        assertEquals(BackupChecksum.calculate(base), BackupChecksum.calculate(reordered))
    }

    @Test
    fun deletedAnswerRepresentation_hashes() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val hash = BackupChecksum.calculate(envelope)
        assertEquals(64, hash.length)
        val answeredWithoutAnswer = envelope.payload.occurrences.count {
            it.status == QuestionOccurrenceStatus.ANSWERED.name &&
                envelope.payload.answers.none { answer ->
                    answer.cycleNumber == it.cycleNumber && answer.cyclePosition == it.cyclePosition
                }
        }
        assertEquals(1, answeredWithoutAnswer)
        assertEquals(
            hash,
            BackupChecksum.calculate(
                envelope.withPayload(
                    envelope.payload.copy(
                        scheduleSlots = listOf(
                            BackupScheduleSlot(1, 900),
                            BackupScheduleSlot(0, 480),
                        ),
                        occurrences = listOf(
                            envelope.payload.occurrences[2],
                            envelope.payload.occurrences[1],
                            envelope.payload.occurrences[0],
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun integrityEncoder_producesDeterministicBytes() {
        val envelope = BackupGoldenFixtures.goldenEnvelopeWithoutChecksum()
        val first = BackupIntegrityEncoderV1.encode(envelope)
        val second = BackupIntegrityEncoderV1.encode(envelope)
        assertEquals(first.toList(), second.toList())
    }

    private fun PraktikaBackupEnvelope.withPayload(payload: PraktikaBackupPayload): PraktikaBackupEnvelope {
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

    private fun PraktikaBackupEnvelope.copyEnvelope(
        backupSequence: Long = this.backupSequence,
        createdAtEpochMillis: Long = this.createdAtEpochMillis,
        sourceSeedVersion: Int = this.sourceSeedVersion,
    ): PraktikaBackupEnvelope {
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

    private fun PraktikaBackupPayload.withAnswers(answers: List<BackupAnswer>): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = practiceState,
            scheduleSlots = scheduleSlots,
            occurrences = occurrences,
            answers = answers,
        )
    }

    private fun PraktikaBackupPayload.copy(
        scheduleSlots: List<BackupScheduleSlot> = this.scheduleSlots,
        occurrences: List<BackupOccurrence> = this.occurrences,
    ): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = practiceState,
            scheduleSlots = scheduleSlots,
            occurrences = occurrences,
            answers = answers,
        )
    }
}
// 11.08.2026 DATA VAULT Stage 1 cursor by Me4Hik END
