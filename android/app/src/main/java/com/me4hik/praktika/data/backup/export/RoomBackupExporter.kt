// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - Room to PraktikaBackupPayload exporter
// PROMPT 111 — export portable deferEvents
package com.me4hik.praktika.data.backup.export

import com.me4hik.praktika.data.backup.integrity.BackupIntegrityEncoderV2
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupDeferEvent
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.BackupScheduleSlot
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.local.entity.DeferEventEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import kotlin.coroutines.cancellation.CancellationException

class RoomBackupExporter(
    private val snapshotReader: RoomBackupSnapshotReader,
) {
    constructor(database: com.me4hik.praktika.data.local.PraktikaDatabase) : this(
        RoomBackupSnapshotReader(database),
    )

    suspend fun export(): BackupExportResult {
        return try {
            val snapshot = snapshotReader.readSnapshot()
            exportSnapshot(snapshot)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            BackupExportResult.ReadFailure(exception.javaClass.name)
        }
    }

    internal fun exportSnapshot(snapshot: RoomBackupSnapshot): BackupExportResult {
        val practiceState = snapshot.practiceState
            ?: return BackupExportResult.DatabaseUnsafe(BackupDatabaseUnsafeReason.MISSING_PRACTICE_STATE)

        val occurrenceStableKeys = snapshot.occurrences.associate { occurrence ->
            occurrence.id to (occurrence.cycleNumber to occurrence.cyclePosition)
        }

        val answers = mutableListOf<BackupAnswer>()
        snapshot.answers.forEach { answer ->
            val stableKey = occurrenceStableKeys[answer.occurrenceId]
                ?: return BackupExportResult.DatabaseUnsafe(BackupDatabaseUnsafeReason.ORPHAN_ANSWER_REFERENCE)
            if (answer.text.isBlank()) {
                return BackupExportResult.DatabaseUnsafe(BackupDatabaseUnsafeReason.BLANK_ANSWER_TEXT)
            }
            answers += BackupAnswer(
                cycleNumber = stableKey.first,
                cyclePosition = stableKey.second,
                text = answer.text,
                createdAtEpochMillis = answer.createdAtEpochMillis,
            )
        }

        val deferEvents = mutableListOf<BackupDeferEvent>()
        snapshot.deferEvents.forEach { event ->
            val mapped = mapDeferEvent(event, occurrenceStableKeys, snapshot.occurrences)
                ?: return BackupExportResult.DatabaseUnsafe(BackupDatabaseUnsafeReason.ORPHAN_DEFER_EVENT_REFERENCE)
            deferEvents += mapped
        }

        val payload = PraktikaBackupPayload(
            practiceState = mapPracticeState(practiceState),
            scheduleSlots = mapScheduleSlots(snapshot.scheduleSlots),
            occurrences = mapOccurrences(snapshot.occurrences),
            answers = sortAnswers(answers),
            deferEvents = deferEvents.sortedWith(BackupIntegrityEncoderV2.DEFER_EVENT_ORDER),
        )

        val unsafeReason = BackupExportDomainValidator.validate(payload)
        if (unsafeReason != null) {
            return BackupExportResult.DatabaseUnsafe(unsafeReason)
        }

        return BackupExportResult.Success(payload)
    }

    private fun mapDeferEvent(
        event: DeferEventEntity,
        occurrenceStableKeys: Map<Long, Pair<Int, Int>>,
        occurrences: List<QuestionOccurrenceEntity>,
    ): BackupDeferEvent? {
        val stableKey = occurrenceStableKeys[event.occurrenceId] ?: return null
        val occurrence = occurrences.firstOrNull { it.id == event.occurrenceId } ?: return null
        if (event.questionId != occurrence.questionId) {
            return null
        }
        return BackupDeferEvent(
            cycleNumber = stableKey.first,
            cyclePosition = stableKey.second,
            questionId = event.questionId,
            occurredAtEpochMillis = event.occurredAtEpochMillis,
            deferredUntilEpochMillis = event.deferredUntilEpochMillis,
            durationMinutes = event.durationMinutes,
            zoneId = event.zoneId,
        )
    }

    private fun mapPracticeState(entity: PracticeStateEntity): BackupPracticeState {
        return BackupPracticeState(
            isPracticeStarted = entity.isPracticeStarted,
            isPaused = entity.isPaused,
            practiceStartedAtEpochMillis = entity.practiceStartedAtEpochMillis,
            currentCycleNumber = entity.currentCycleNumber,
            nextCyclePosition = entity.nextCyclePosition,
            lastProcessedAtEpochMillis = entity.lastProcessedAtEpochMillis,
            pausedAtEpochMillis = entity.pausedAtEpochMillis,
            activeZoneId = entity.activeZoneId,
            seedVersion = entity.seedVersion,
        )
    }

    private fun mapScheduleSlots(entities: List<ScheduleSlotEntity>): List<BackupScheduleSlot> {
        return entities
            .map { entity ->
                BackupScheduleSlot(
                    slotIndex = entity.slotIndex,
                    timeOfDayMinutes = entity.timeOfDayMinutes,
                )
            }
            .sortedBy { it.slotIndex }
    }

    private fun mapOccurrences(entities: List<QuestionOccurrenceEntity>): List<BackupOccurrence> {
        return entities
            .map { entity ->
                BackupOccurrence(
                    questionId = entity.questionId,
                    questionTextSnapshot = entity.questionTextSnapshot,
                    cycleNumber = entity.cycleNumber,
                    cyclePosition = entity.cyclePosition,
                    scheduleSlotIndex = entity.scheduleSlotIndex,
                    plannedAtEpochMillis = entity.plannedAtEpochMillis,
                    availableUntilEpochMillis = entity.availableUntilEpochMillis,
                    openedAtEpochMillis = entity.openedAtEpochMillis,
                    completedAtEpochMillis = entity.completedAtEpochMillis,
                    deferredUntilEpochMillis = entity.deferredUntilEpochMillis,
                    status = entity.status.name,
                    zoneId = entity.zoneId,
                )
            }
            .sortedWith(compareBy({ it.cycleNumber }, { it.cyclePosition }))
    }

    private fun sortAnswers(answers: List<BackupAnswer>): List<BackupAnswer> {
        return answers.sortedWith(compareBy({ it.cycleNumber }, { it.cyclePosition }))
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
