package com.me4hik.praktika.ui.restore

import com.me4hik.praktika.data.backup.codec.BackupJsonEncoder
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssembler
import com.me4hik.praktika.data.backup.envelope.BackupEnvelopeAssemblyResult
import com.me4hik.praktika.data.backup.metadata.BackupClock
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.metadata.BuildConfigBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.metadata.SystemBackupClock
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.cycle.ScheduleCalculator
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.SeedDataValidator
import com.me4hik.praktika.ui.saf.SyntheticEnvelopeBundle

class RichRestoreAcceptanceEnvelopeFactory(
    private val acceptanceClock: BackupClock = SystemBackupClock,
    private val metadataFactory: BackupSnapshotMetadataFactory = BackupSnapshotMetadataFactory(
        clock = acceptanceClock,
        appMetadataProvider = BuildConfigBackupAppMetadataProvider(),
    ),
) {
    fun createSlotA(): SyntheticEnvelopeBundle? = assemble(notStartedPayload(), sequence = 1L)

    fun createSlotB(): SyntheticEnvelopeBundle? = assemble(richPayload(), sequence = 2L)

    private fun assemble(payload: PraktikaBackupPayload, sequence: Long): SyntheticEnvelopeBundle? {
        val metadata = metadataFactory.capture()
        return when (
            val assembled = BackupEnvelopeAssembler.assemble(
                payload = payload,
                backupSequence = sequence,
                metadata = metadata,
            )
        ) {
            is BackupEnvelopeAssemblyResult.Success -> {
                val envelope = assembled.envelope
                SyntheticEnvelopeBundle(
                    envelope = envelope,
                    bytes = BackupJsonEncoder.encodeToUtf8Bytes(envelope),
                )
            }

            else -> null
        }
    }

    private fun notStartedPayload(): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = false,
                isPaused = false,
                practiceStartedAtEpochMillis = null,
                currentCycleNumber = 0,
                nextCyclePosition = 1,
                lastProcessedAtEpochMillis = null,
                pausedAtEpochMillis = null,
                activeZoneId = ZONE_MOSCOW,
                seedVersion = SeedDataValidator.EXPECTED_SEED_VERSION,
            ),
            scheduleSlots = notStartedSchedule,
            occurrences = emptyList(),
            answers = emptyList(),
        )
    }

    private fun richPayload(): PraktikaBackupPayload {
        val anchorMillis = acceptanceClock.nowEpochMillis()
        return PraktikaBackupPayload(
            practiceState = BackupPracticeState(
                isPracticeStarted = true,
                isPaused = false,
                practiceStartedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS,
                currentCycleNumber = 1,
                nextCyclePosition = 4,
                lastProcessedAtEpochMillis = anchorMillis - ACCEPTANCE_LAST_PROCESSED_OFFSET_MILLIS,
                pausedAtEpochMillis = null,
                activeZoneId = ZONE_MOSCOW,
                seedVersion = SeedDataValidator.EXPECTED_SEED_VERSION,
            ),
            scheduleSlots = richSchedule,
            occurrences = richOccurrences(anchorMillis),
            answers = richAnswers(anchorMillis),
        )
    }

    private fun richOccurrences(anchorMillis: Long): List<BackupOccurrence> {
        val (plannedMoment, availableUntil) = plannedMomentAndAvailableUntil(anchorMillis)
        return listOf(
            BackupOccurrence(
                questionId = 1,
                questionTextSnapshot = "Historical snapshot for question 1",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS,
                availableUntilEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 3_500_000L,
                openedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 50_000L,
                completedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 100_000L,
                status = QuestionOccurrenceStatus.ANSWERED.name,
                zoneId = ZONE_MOSCOW,
            ),
            BackupOccurrence(
                questionId = 2,
                questionTextSnapshot = "Deleted answer snapshot for question 2",
                cycleNumber = 1,
                cyclePosition = 2,
                scheduleSlotIndex = 2,
                plannedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 1_000_000L,
                availableUntilEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 4_500_000L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 150_000L,
                status = QuestionOccurrenceStatus.ANSWERED.name,
                zoneId = ZONE_MOSCOW,
            ),
            BackupOccurrence(
                questionId = 3,
                questionTextSnapshot = "Skipped snapshot for question 3",
                cycleNumber = 1,
                cyclePosition = 3,
                scheduleSlotIndex = 3,
                plannedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 2_000_000L,
                availableUntilEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 5_700_000L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 220_000L,
                status = QuestionOccurrenceStatus.SKIPPED_BY_USER.name,
                zoneId = ZONE_MOSCOW,
            ),
            BackupOccurrence(
                questionId = 4,
                questionTextSnapshot = "Current scheduled snapshot for question 4",
                cycleNumber = 1,
                cyclePosition = 4,
                scheduleSlotIndex = plannedMoment.slotIndex,
                plannedAtEpochMillis = plannedMoment.plannedAtEpochMillis,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = null,
                status = QuestionOccurrenceStatus.SCHEDULED.name,
                zoneId = ZONE_MOSCOW,
            ),
        )
    }

    private fun richAnswers(anchorMillis: Long): List<BackupAnswer> {
        return listOf(
            BackupAnswer(
                cycleNumber = 1,
                cyclePosition = 1,
                text = "Answer preserved exactly",
                createdAtEpochMillis = anchorMillis - ACCEPTANCE_HISTORY_OFFSET_MILLIS + 110_000L,
            ),
        )
    }

    private fun plannedMomentAndAvailableUntil(anchorMillis: Long): Pair<
        com.me4hik.praktika.data.cycle.SlotMoment,
        Long,
        > {
        val afterEpochMillis = anchorMillis + ACCEPTANCE_FUTURE_OFFSET_MILLIS
        val scheduleCalculator = ScheduleCalculator()
        val slots = richSchedule.map { slot ->
            ScheduleSlotEntity(
                slotIndex = slot.slotIndex,
                timeOfDayMinutes = slot.timeOfDayMinutes,
            )
        }
        val plannedMoment = scheduleCalculator.findStrictlyNextSlot(
            afterEpochMillis = afterEpochMillis,
            zoneId = ZONE_MOSCOW,
            slots = slots,
        )
        val availableUntil = scheduleCalculator.calculateAvailableUntil(
            plannedMoment = plannedMoment,
            slots = slots,
        )
        return plannedMoment to availableUntil
    }

    companion object {
        const val ZONE_MOSCOW = "Europe/Moscow"
        private const val ACCEPTANCE_HISTORY_OFFSET_MILLIS = 250_000L
        private const val ACCEPTANCE_LAST_PROCESSED_OFFSET_MILLIS = 50_000L
        private const val ACCEPTANCE_FUTURE_OFFSET_MILLIS = 7L * 24L * 60L * 60L * 1_000L

        val notStartedSchedule: List<BackupScheduleSlot> = listOf(
            BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 480),
            BackupScheduleSlot(slotIndex = 2, timeOfDayMinutes = 720),
            BackupScheduleSlot(slotIndex = 3, timeOfDayMinutes = 1020),
        )

        val richSchedule: List<BackupScheduleSlot> = listOf(
            BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 900),
            BackupScheduleSlot(slotIndex = 2, timeOfDayMinutes = 660),
            BackupScheduleSlot(slotIndex = 3, timeOfDayMinutes = 1140),
        )
    }
}
