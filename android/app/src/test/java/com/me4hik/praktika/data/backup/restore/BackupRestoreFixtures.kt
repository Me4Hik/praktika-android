// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1 fixtures
// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik START - runtime-valid rich restore fixture
package com.me4hik.praktika.data.backup.restore

import com.me4hik.praktika.data.backup.BackupConstants
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.cycle.ScheduleCalculator
import com.me4hik.praktika.data.cycle.SlotMoment
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.SeedDataValidator

object BackupRestoreFixtures {
    const val SOURCE_APP_VERSION_CODE = 7
    const val SOURCE_APP_VERSION_NAME = "1.0"
    const val BACKUP_SEQUENCE = 99L
    const val CREATED_AT_EPOCH_MILLIS = 1_700_000_300_000L
    const val ZONE_MOSCOW = "Europe/Moscow"
    const val HOST_ACCEPTANCE_TIME_ANCHOR_MILLIS = 1_700_000_250_000L
    private const val ACCEPTANCE_FUTURE_OFFSET_MILLIS = 7L * 24L * 60L * 60L * 1_000L

    val defaultTargetScheduleMinutes = listOf(660, 900, 1140)

    val collisionSchedule: List<BackupScheduleSlot> = listOf(
        BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 900),
        BackupScheduleSlot(slotIndex = 2, timeOfDayMinutes = 660),
        BackupScheduleSlot(slotIndex = 3, timeOfDayMinutes = 1140),
    )

    val customAscendingSchedule: List<BackupScheduleSlot> = listOf(
        BackupScheduleSlot(slotIndex = 1, timeOfDayMinutes = 480),
        BackupScheduleSlot(slotIndex = 2, timeOfDayMinutes = 720),
        BackupScheduleSlot(slotIndex = 3, timeOfDayMinutes = 1020),
    )

    private val scheduleCalculator = ScheduleCalculator()

    private val collisionScheduleEntities: List<ScheduleSlotEntity> by lazy {
        collisionSchedule.map { slot ->
            ScheduleSlotEntity(
                slotIndex = slot.slotIndex,
                timeOfDayMinutes = slot.timeOfDayMinutes,
            )
        }
    }

    val richCurrentOccurrenceTiming: Pair<SlotMoment, Long> by lazy {
        plannedMomentAndAvailableUntil(
            afterEpochMillis = HOST_ACCEPTANCE_TIME_ANCHOR_MILLIS + ACCEPTANCE_FUTURE_OFFSET_MILLIS,
        )
    }

    fun acceptanceReconcileNowMillis(): Long = HOST_ACCEPTANCE_TIME_ANCHOR_MILLIS

    fun plannedMomentAndAvailableUntil(afterEpochMillis: Long): Pair<SlotMoment, Long> {
        val plannedMoment = scheduleCalculator.findStrictlyNextSlot(
            afterEpochMillis = afterEpochMillis,
            zoneId = ZONE_MOSCOW,
            slots = collisionScheduleEntities,
        )
        val availableUntil = scheduleCalculator.calculateAvailableUntil(
            plannedMoment = plannedMoment,
            slots = collisionScheduleEntities,
        )
        return plannedMoment to availableUntil
    }

    fun richPracticeState(): BackupPracticeState {
        return BackupPracticeState(
            isPracticeStarted = true,
            isPaused = false,
            practiceStartedAtEpochMillis = 1_700_000_000_000L,
            currentCycleNumber = 1,
            nextCyclePosition = 4,
            lastProcessedAtEpochMillis = HOST_ACCEPTANCE_TIME_ANCHOR_MILLIS,
            pausedAtEpochMillis = null,
            activeZoneId = ZONE_MOSCOW,
            seedVersion = SeedDataValidator.EXPECTED_SEED_VERSION,
        )
    }

    fun richOccurrences(): List<BackupOccurrence> {
        val (plannedMoment, availableUntil) = richCurrentOccurrenceTiming
        return listOf(
            BackupOccurrence(
                questionId = 1,
                questionTextSnapshot = "Historical snapshot for question 1",
                cycleNumber = 1,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = 1_699_998_000_000L,
                availableUntilEpochMillis = 1_700_001_500_000L,
                openedAtEpochMillis = 1_700_000_050_000L,
                completedAtEpochMillis = 1_700_000_100_000L,
                status = QuestionOccurrenceStatus.ANSWERED.name,
                zoneId = ZONE_MOSCOW,
            ),
            BackupOccurrence(
                questionId = 2,
                questionTextSnapshot = "Deleted answer snapshot for question 2",
                cycleNumber = 1,
                cyclePosition = 2,
                scheduleSlotIndex = 2,
                plannedAtEpochMillis = 1_699_999_000_000L,
                availableUntilEpochMillis = 1_700_002_500_000L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = 1_700_000_150_000L,
                status = QuestionOccurrenceStatus.ANSWERED.name,
                zoneId = ZONE_MOSCOW,
            ),
            BackupOccurrence(
                questionId = 3,
                questionTextSnapshot = "Skipped snapshot for question 3",
                cycleNumber = 1,
                cyclePosition = 3,
                scheduleSlotIndex = 3,
                plannedAtEpochMillis = 1_700_000_200_000L,
                availableUntilEpochMillis = 1_700_003_700_000L,
                openedAtEpochMillis = null,
                completedAtEpochMillis = 1_700_000_220_000L,
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

    fun richAnswers(): List<BackupAnswer> {
        return listOf(
            BackupAnswer(
                cycleNumber = 1,
                cyclePosition = 1,
                text = "Answer preserved exactly",
                createdAtEpochMillis = 1_700_000_110_000L,
            ),
        )
    }

    fun richPayload(
        scheduleSlots: List<BackupScheduleSlot> = collisionSchedule,
    ): PraktikaBackupPayload {
        return PraktikaBackupPayload(
            practiceState = richPracticeState(),
            scheduleSlots = scheduleSlots,
            occurrences = richOccurrences(),
            answers = richAnswers(),
        )
    }

    fun richEnvelope(
        scheduleSlots: List<BackupScheduleSlot> = collisionSchedule,
        seedVersion: Int = SeedDataValidator.EXPECTED_SEED_VERSION,
    ): PraktikaBackupEnvelope {
        val baseState = richPracticeState()
        val practiceState = BackupPracticeState(
            isPracticeStarted = baseState.isPracticeStarted,
            isPaused = baseState.isPaused,
            practiceStartedAtEpochMillis = baseState.practiceStartedAtEpochMillis,
            currentCycleNumber = baseState.currentCycleNumber,
            nextCyclePosition = baseState.nextCyclePosition,
            lastProcessedAtEpochMillis = baseState.lastProcessedAtEpochMillis,
            pausedAtEpochMillis = baseState.pausedAtEpochMillis,
            activeZoneId = baseState.activeZoneId,
            seedVersion = seedVersion,
        )
        val payload = PraktikaBackupPayload(
            practiceState = practiceState,
            scheduleSlots = scheduleSlots,
            occurrences = richOccurrences(),
            answers = richAnswers(),
        )
        return envelope(payload, seedVersion = seedVersion)
    }

    fun notStartedPayload(): PraktikaBackupPayload {
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
            scheduleSlots = customAscendingSchedule,
            occurrences = emptyList(),
            answers = emptyList(),
        )
    }

    fun envelope(
        payload: PraktikaBackupPayload,
        seedVersion: Int = payload.practiceState.seedVersion,
    ): PraktikaBackupEnvelope {
        return PraktikaBackupEnvelope(
            backupSchemaVersion = BackupConstants.BACKUP_SCHEMA_VERSION_V1,
            backupSequence = BACKUP_SEQUENCE,
            createdAtEpochMillis = CREATED_AT_EPOCH_MILLIS,
            sourceAppVersionCode = SOURCE_APP_VERSION_CODE,
            sourceAppVersionName = SOURCE_APP_VERSION_NAME,
            sourceSeedVersion = seedVersion,
            backupChecksumSha256 = "0".repeat(BackupConstants.CHECKSUM_HEX_LENGTH),
            payload = payload,
        )
    }
}

// 11.08.2026 DATA VAULT Stage 4.3 cursor by Me4Hik END
// 10.08.2026 Post-release fixes cursor by Me4Hik END
