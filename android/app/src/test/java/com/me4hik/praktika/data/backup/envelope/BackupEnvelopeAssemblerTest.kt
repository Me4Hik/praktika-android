// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 2.5 envelope assembly
package com.me4hik.praktika.data.backup.envelope

import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataContext
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.validate.BackupFormatValidationResult
import com.me4hik.praktika.data.backup.validate.BackupFormatValidator
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEnvelopeAssemblerTest {
    @Test
    fun assemble_validPayload_returnsSuccess() {
        val payload = validPayload(seedVersion = 1)
        val metadata = metadata(
            createdAt = 1_700_000_300_000L,
            versionCode = 7,
            versionName = "1.0",
        )

        val result = BackupEnvelopeAssembler.assemble(
            payload = payload,
            backupSequence = 42L,
            metadata = metadata,
        )

        assertTrue(result is BackupEnvelopeAssemblyResult.Success)
        val envelope = (result as BackupEnvelopeAssemblyResult.Success).envelope
        assertEquals(BackupConstants.BACKUP_SCHEMA_VERSION_V2, envelope.backupSchemaVersion)
        assertEquals(42L, envelope.backupSequence)
        assertEquals(1_700_000_300_000L, envelope.createdAtEpochMillis)
        assertEquals(7, envelope.sourceAppVersionCode)
        assertEquals("1.0", envelope.sourceAppVersionName)
        assertEquals(1, envelope.sourceSeedVersion)
        assertEquals(payload, envelope.payload)
        assertEquals(64, envelope.backupChecksumSha256.length)
        assertTrue(envelope.backupChecksumSha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun sourceSeedVersion_derivedFromPayload() {
        val payload = validPayload(seedVersion = 9)
        val result = BackupEnvelopeAssembler.assemble(
            payload = payload,
            backupSequence = 1L,
            metadata = metadata(),
        )

        assertTrue(result is BackupEnvelopeAssemblyResult.Success)
        assertEquals(
            9,
            (result as BackupEnvelopeAssemblyResult.Success).envelope.sourceSeedVersion,
        )
    }

    @Test
    fun checksum_matchesBackupChecksumCalculate() {
        val result = BackupEnvelopeAssembler.assemble(
            payload = validPayload(),
            backupSequence = 42L,
            metadata = metadata(),
        )

        val envelope = (result as BackupEnvelopeAssemblyResult.Success).envelope
        assertEquals(BackupChecksum.calculate(envelope), envelope.backupChecksumSha256)
    }

    @Test
    fun assembledEnvelope_formatValidatorPasses() {
        val result = BackupEnvelopeAssembler.assemble(
            payload = validPayload(),
            backupSequence = 42L,
            metadata = metadata(),
        )

        val envelope = (result as BackupEnvelopeAssemblyResult.Success).envelope
        assertTrue(BackupFormatValidator.validate(envelope) is BackupFormatValidationResult.Valid)
    }

    @Test
    fun differentSequence_changesChecksum() {
        val payload = validPayload()
        val metadata = metadata()
        val first = successEnvelope(payload, 1L, metadata)
        val second = successEnvelope(payload, 2L, metadata)
        assertNotEquals(first.backupChecksumSha256, second.backupChecksumSha256)
    }

    @Test
    fun differentCreatedAt_changesChecksum() {
        val payload = validPayload()
        val first = successEnvelope(payload, 1L, metadata(createdAt = 1_700_000_000_000L))
        val second = successEnvelope(payload, 1L, metadata(createdAt = 1_700_000_001_000L))
        assertNotEquals(first.backupChecksumSha256, second.backupChecksumSha256)
    }

    @Test
    fun differentVersionCode_changesChecksum() {
        val payload = validPayload()
        val first = successEnvelope(payload, 1L, metadata(versionCode = 7))
        val second = successEnvelope(payload, 1L, metadata(versionCode = 8))
        assertNotEquals(first.backupChecksumSha256, second.backupChecksumSha256)
    }

    @Test
    fun differentVersionName_changesChecksum() {
        val payload = validPayload()
        val first = successEnvelope(payload, 1L, metadata(versionName = "1.0"))
        val second = successEnvelope(payload, 1L, metadata(versionName = "1.0-accelerated"))
        assertNotEquals(first.backupChecksumSha256, second.backupChecksumSha256)
    }

    @Test
    fun sequenceZero_invalidSequence() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidSequence,
            BackupEnvelopeAssembler.assemble(validPayload(), 0L, metadata()),
        )
    }

    @Test
    fun sequenceNegative_invalidSequence() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidSequence,
            BackupEnvelopeAssembler.assemble(validPayload(), -1L, metadata()),
        )
    }

    @Test
    fun createdAtZero_invalidTimestamp() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidTimestamp,
            BackupEnvelopeAssembler.assemble(validPayload(), 1L, metadata(createdAt = 0L)),
        )
    }

    @Test
    fun createdAtNegative_invalidTimestamp() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidTimestamp,
            BackupEnvelopeAssembler.assemble(validPayload(), 1L, metadata(createdAt = -1L)),
        )
    }

    @Test
    fun versionCodeZero_invalidAppMetadata() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidAppMetadata,
            BackupEnvelopeAssembler.assemble(validPayload(), 1L, metadata(versionCode = 0)),
        )
    }

    @Test
    fun versionNameBlank_invalidAppMetadata() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidAppMetadata,
            BackupEnvelopeAssembler.assemble(validPayload(), 1L, metadata(versionName = "")),
        )
    }

    @Test
    fun versionNameWhitespace_invalidAppMetadata() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidAppMetadata,
            BackupEnvelopeAssembler.assemble(validPayload(), 1L, metadata(versionName = "   ")),
        )
    }

    @Test
    fun seedVersionZero_invalidSeedVersion() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidSeedVersion,
            BackupEnvelopeAssembler.assemble(validPayload(seedVersion = 0), 1L, metadata()),
        )
    }

    @Test
    fun seedVersionNegative_invalidSeedVersion() {
        assertEquals(
            BackupEnvelopeAssemblyResult.InvalidSeedVersion,
            BackupEnvelopeAssembler.assemble(validPayload(seedVersion = -1), 1L, metadata()),
        )
    }

    @Test
    fun longMaxSequence_allowed() {
        val result = BackupEnvelopeAssembler.assemble(
            payload = validPayload(),
            backupSequence = Long.MAX_VALUE,
            metadata = metadata(),
        )
        assertTrue(result is BackupEnvelopeAssemblyResult.Success)
        assertEquals(Long.MAX_VALUE, (result as BackupEnvelopeAssemblyResult.Success).envelope.backupSequence)
    }

    @Test
    fun inputPayload_notMutated() {
        val payload = validPayload()
        val scheduleSlotsBefore = payload.scheduleSlots.toList()
        val occurrencesBefore = payload.occurrences.toList()
        val answersBefore = payload.answers.toList()
        val practiceStateBefore = payload.practiceState

        val result = BackupEnvelopeAssembler.assemble(payload, 1L, metadata())

        assertSame(payload, (result as BackupEnvelopeAssemblyResult.Success).envelope.payload)
        assertEquals(scheduleSlotsBefore, payload.scheduleSlots)
        assertEquals(occurrencesBefore, payload.occurrences)
        assertEquals(answersBefore, payload.answers)
        assertEquals(practiceStateBefore, payload.practiceState)
    }

    private fun successEnvelope(
        payload: PraktikaBackupPayload,
        backupSequence: Long,
        metadata: BackupSnapshotMetadataContext,
    ): PraktikaBackupEnvelope {
        val result = BackupEnvelopeAssembler.assemble(payload, backupSequence, metadata)
        assertTrue(result is BackupEnvelopeAssemblyResult.Success)
        return (result as BackupEnvelopeAssemblyResult.Success).envelope
    }

    private fun metadata(
        createdAt: Long = 1_700_000_300_000L,
        versionCode: Int = 7,
        versionName: String = "1.0",
    ): BackupSnapshotMetadataContext {
        return BackupSnapshotMetadataContext(
            createdAtEpochMillis = createdAt,
            sourceAppVersionCode = versionCode,
            sourceAppVersionName = versionName,
        )
    }

    private fun validPayload(seedVersion: Int = 1): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = "Europe/Kiev",
                seedVersion = seedVersion,
            ),
            scheduleSlots = listOf(
                BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 660),
                BackupScheduleSlot(slotIndex = 2, timeOfDayMinutes = 900),
                BackupScheduleSlot(slotIndex = 3, timeOfDayMinutes = 1140),
            ),
            occurrences = listOf(
                BackupOccurrence(
                    questionId = 1,
                    questionTextSnapshot = "Question 1",
                    cycleNumber = 1,
                    cyclePosition = 1,
                    scheduleSlotIndex = 1,
                    plannedAtEpochMillis = 1_000L,
                    availableUntilEpochMillis = 2_000L,
                    openedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    status = QuestionOccurrenceStatus.SCHEDULED.name,
                    zoneId = "Europe/Kiev",
                ),
            ),
            answers = emptyList(),
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
