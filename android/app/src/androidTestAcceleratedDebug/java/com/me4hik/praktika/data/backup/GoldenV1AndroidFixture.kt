// 11.08.2026 DATA VAULT Stage 1.5 cursor by Me4Hik START - accelerated androidTest golden fixture
package com.me4hik.praktika.data.backup

import com.me4hik.praktika.data.backup.checksum.BackupChecksum
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

object GoldenV1AndroidFixture {
    const val GOLDEN_INTEGRITY_HASH_V1 =
        "74be6a697d8d5e2e987264e2a6ce6d7f0245a210f5bb4421dcc4b952b9a43a20"

    private val goldenPracticeState = BackupPracticeState(
        isPracticeStarted = true,
        isPaused = false,
        practiceStartedAtEpochMillis = 1_700_000_000_000L,
        currentCycleNumber = 2,
        nextCyclePosition = 3,
        lastProcessedAtEpochMillis = null,
        pausedAtEpochMillis = null,
        activeZoneId = "Europe/Moscow",
        seedVersion = 1,
    )

    private val goldenPayloadUnsorted = PraktikaBackupPayload(
        practiceState = goldenPracticeState,
        scheduleSlots = listOf(
            BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 900),
            BackupScheduleSlot(slotIndex = 0, timeOfDayMinutes = 480),
        ),
        occurrences = listOf(
            BackupOccurrence(
                questionId = 10,
                questionTextSnapshot = "Вопрос с кириллицей 😀 \"quotes\" \\backslash\nnewline",
                cycleNumber = 2,
                cyclePosition = 1,
                scheduleSlotIndex = 0,
                plannedAtEpochMillis = 1_700_000_100_000L,
                availableUntilEpochMillis = 1_700_003_600_000L,
                openedAtEpochMillis = 1_700_000_150_000L,
                completedAtEpochMillis = 1_700_000_200_000L,
                status = QuestionOccurrenceStatus.ANSWERED.name,
                zoneId = "Europe/Moscow",
            ),
            BackupOccurrence(
                questionId = 11,
                questionTextSnapshot = "Deleted answer occurrence",
                cycleNumber = 1,
                cyclePosition = 2,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1_699_999_000_000L,
                availableUntilEpochMillis = 1_700_002_500_000L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = 1_700_000_050_000L,
                status = QuestionOccurrenceStatus.ANSWERED.name,
                zoneId = "Europe/Moscow",
            ),
            BackupOccurrence(
                questionId = 12,
                questionTextSnapshot = "Missed question",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 0,
                plannedAtEpochMillis = 1_699_998_000_000L,
                availableUntilEpochMillis = 1_700_001_500_000L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.MISSED_BY_TIME.name,
                zoneId = "Europe/Moscow",
            ),
        ),
        answers = listOf(
            BackupAnswer(
                cycleNumber = 2,
                cyclePosition = 1,
                text = "Ответ с emoji 🎯 и \"кавычками\"",
                createdAtEpochMillis = 1_700_000_210_000L,
            ),
        ),
    )

    fun goldenEnvelopeWithoutChecksum(): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = BackupConstants.BACKUP_SCHEMA_VERSION_V1,
            backupSequence = 42L,
            createdAtEpochMillis = 1_700_000_300_000L,
            sourceAppVersionCode = 7,
            sourceAppVersionName = "1.0",
            sourceSeedVersion = 1,
            backupChecksumSha256 = "",
            payload = goldenPayloadUnsorted,
        )
    }

    fun goldenEnvelopeWithChecksum(): PraktikaBackupEnvelope {
        return withCanonicalPayloadOrder(goldenEnvelopeWithoutChecksum()).let { withoutChecksum ->
            val checksum = BackupChecksum.calculate(withoutChecksum)
            withoutChecksum.copyChecksum(checksum)
        }
    }

    fun withCanonicalPayloadOrder(envelope: PraktikaBackupEnvelope): PraktikaBackupEnvelope {
        val payload = envelope.payload
        return PraktikaBackupEnvelope(
            backupSchemaVersion = envelope.backupSchemaVersion,
            backupSequence = envelope.backupSequence,
            createdAtEpochMillis = envelope.createdAtEpochMillis,
            sourceAppVersionCode = envelope.sourceAppVersionCode,
            sourceAppVersionName = envelope.sourceAppVersionName,
            sourceSeedVersion = envelope.sourceSeedVersion,
            backupChecksumSha256 = envelope.backupChecksumSha256,
            payload = PraktikaBackupPayload(
                practiceState = payload.practiceState,
                scheduleSlots = payload.scheduleSlots.sortedBy { it.slotIndex },
                occurrences = payload.occurrences.sortedWith(
                    compareBy({ it.cycleNumber }, { it.cyclePosition }),
                ),
                answers = payload.answers.sortedWith(
                    compareBy({ it.cycleNumber }, { it.cyclePosition }),
                ),
            ),
        )
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
}
// 11.08.2026 DATA VAULT Stage 1.5 cursor by Me4Hik END
