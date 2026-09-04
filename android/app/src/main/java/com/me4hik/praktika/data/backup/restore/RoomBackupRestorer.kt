// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1
package com.me4hik.praktika.data.backup.restore

import androidx.room.withTransaction
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.export.RoomBackupSnapshot
import com.me4hik.praktika.data.backup.model.BackupAnswer
import com.me4hik.praktika.data.backup.model.BackupOccurrence
import com.me4hik.praktika.data.backup.model.BackupPracticeState
import com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope
import com.me4hik.praktika.data.cycle.CursorConsistencyKind
import com.me4hik.praktika.data.cycle.CycleCursorConsistency
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.SeedDataValidator
import kotlin.coroutines.cancellation.CancellationException

internal typealias StableOccurrenceKey = Pair<Int, Int>

class RoomBackupRestorer(
    private val database: PraktikaDatabase,
    private val domainValidator: BackupRestoreDomainValidator = BackupRestoreDomainValidator(),
    private val targetValidator: BackupRestoreTargetValidator = BackupRestoreTargetValidator(),
    private val exporter: RoomBackupExporter = RoomBackupExporter(database),
) : BackupRestoreEngine {
    internal var transactionProbe: RestoreTransactionProbe? = null

    override suspend fun restore(envelope: PraktikaBackupEnvelope): BackupRestoreResult {
        domainValidator.validateEnvelope(envelope)?.let { reason ->
            return BackupRestoreResult.InvalidBackup(reason)
        }
        domainValidator.validateStartedRequiresOccurrences(
            state = envelope.payload.practiceState,
            occurrences = envelope.payload.occurrences,
        )?.let { reason ->
            return BackupRestoreResult.InvalidBackup(reason)
        }

        return try {
            database.withTransaction {
                restoreWithinTransaction(envelope)
            }
            BackupRestoreResult.Success
        } catch (abort: RestoreAbortException) {
            abort.result
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (exception: Exception) {
            BackupRestoreResult.DatabaseWriteFailure(exception.javaClass.name)
        }
    }

    private suspend fun restoreWithinTransaction(envelope: PraktikaBackupEnvelope) {
        targetValidator.validateFreshEmptyTarget(database)?.let { result ->
            throw RestoreAbortException(result)
        }

        val targetState = database.practiceStateDao().get()
            ?: throw RestoreAbortException(
                BackupRestoreResult.TargetDatabaseUnsafe(
                    TargetDatabaseUnsafeReason.MISSING_PRACTICE_STATE,
                ),
            )

        validateSeedCompatibility(
            backupSeed = envelope.sourceSeedVersion,
            targetSeed = targetState.seedVersion,
        )?.let { result ->
            throw RestoreAbortException(result)
        }

        validateStaticQuestions(envelope.payload.occurrences)?.let { result ->
            throw RestoreAbortException(result)
        }

        ScheduleSlotRestoreWriter.applyScheduleRestore(
            slotDao = database.scheduleSlotDao(),
            scheduleSlots = envelope.payload.scheduleSlots,
        )
        transactionProbe?.afterSchedule?.invoke()

        val occurrenceIds = insertOccurrences(envelope.payload.occurrences)
        insertAnswers(envelope.payload.answers, occurrenceIds)

        database.practiceStateDao().upsert(
            mapPracticeState(envelope.payload.practiceState),
        )

        validatePostRestore(envelope.payload)
    }

    private fun validateSeedCompatibility(
        backupSeed: Int,
        targetSeed: Int,
    ): BackupRestoreResult? {
        return when {
            backupSeed < targetSeed -> BackupRestoreResult.IncompatibleSeed(
                backupSeed = backupSeed,
                targetSeed = targetSeed,
                classification = IncompatibleSeedClassification.MIGRATION_REQUIRED,
            )

            backupSeed > targetSeed -> BackupRestoreResult.IncompatibleSeed(
                backupSeed = backupSeed,
                targetSeed = targetSeed,
                classification = IncompatibleSeedClassification.NEWER_THAN_INSTALLED,
            )

            backupSeed != targetSeed -> BackupRestoreResult.IncompatibleSeed(
                backupSeed = backupSeed,
                targetSeed = targetSeed,
                classification = IncompatibleSeedClassification.MIGRATION_REQUIRED,
            )

            else -> null
        }
    }

    private suspend fun validateStaticQuestions(
        occurrences: List<BackupOccurrence>,
    ): BackupRestoreResult? {
        occurrences.forEach { occurrence ->
            val question = database.questionDao().getById(occurrence.questionId)
            if (question == null) {
                return BackupRestoreResult.StaticQuestionMismatch(
                    questionId = occurrence.questionId,
                    reason = StaticQuestionMismatchReason.QUESTION_NOT_FOUND,
                )
            }
            if (question.cyclePosition != occurrence.cyclePosition) {
                return BackupRestoreResult.StaticQuestionMismatch(
                    questionId = occurrence.questionId,
                    reason = StaticQuestionMismatchReason.QUESTION_NOT_FOUND,
                )
            }
        }
        return null
    }

    private suspend fun insertOccurrences(
        occurrences: List<BackupOccurrence>,
    ): Map<StableOccurrenceKey, Long> {
        val occurrenceDao = database.questionOccurrenceDao()
        val ids = linkedMapOf<StableOccurrenceKey, Long>()
        occurrences.sortedWith(compareBy({ it.cycleNumber }, { it.cyclePosition })).forEach { backup ->
            val entity = QuestionOccurrenceEntity(
                questionId = backup.questionId,
                questionTextSnapshot = backup.questionTextSnapshot,
                cycleNumber = backup.cycleNumber,
                cyclePosition = backup.cyclePosition,
                scheduleSlotIndex = backup.scheduleSlotIndex,
                plannedAtEpochMillis = backup.plannedAtEpochMillis,
                availableUntilEpochMillis = backup.availableUntilEpochMillis,
                openedAtEpochMillis = backup.openedAtEpochMillis,
                completedAtEpochMillis = backup.completedAtEpochMillis,
                deferredUntilEpochMillis = backup.deferredUntilEpochMillis,
                status = QuestionOccurrenceStatus.valueOf(backup.status),
                zoneId = backup.zoneId,
            )
            val id = occurrenceDao.insert(entity)
            ids[backup.cycleNumber to backup.cyclePosition] = id
        }
        return ids
    }

    private suspend fun insertAnswers(
        answers: List<BackupAnswer>,
        occurrenceIds: Map<StableOccurrenceKey, Long>,
    ) {
        val answerDao = database.answerDao()
        answers.sortedWith(compareBy({ it.cycleNumber }, { it.cyclePosition })).forEach { backup ->
            val occurrenceId = occurrenceIds[backup.cycleNumber to backup.cyclePosition]
                ?: throw RestoreAbortException(
                    BackupRestoreResult.InvalidBackup(
                        BackupRestoreDomainFailureReason.ORPHAN_ANSWER_REFERENCE,
                    ),
                )
            answerDao.insert(
                AnswerEntity(
                    occurrenceId = occurrenceId,
                    text = backup.text,
                    createdAtEpochMillis = backup.createdAtEpochMillis,
                ),
            )
        }
    }

    private fun mapPracticeState(state: BackupPracticeState): PracticeStateEntity {
        return PracticeStateEntity(
            id = 1,
            isPracticeStarted = state.isPracticeStarted,
            isPaused = state.isPaused,
            practiceStartedAtEpochMillis = state.practiceStartedAtEpochMillis,
            currentCycleNumber = state.currentCycleNumber,
            nextCyclePosition = state.nextCyclePosition,
            lastProcessedAtEpochMillis = state.lastProcessedAtEpochMillis,
            pausedAtEpochMillis = state.pausedAtEpochMillis,
            activeZoneId = state.activeZoneId,
            seedVersion = state.seedVersion,
        )
    }

    private suspend fun validatePostRestore(expectedPayload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload) {
        val snapshot = readTransactionSnapshot()
        when (val exportResult = exporter.exportSnapshot(snapshot)) {
            is BackupExportResult.Success -> {
                if (!BackupPayloadSemanticComparator.equalsSemantically(expectedPayload, exportResult.payload)) {
                    throw RestoreAbortException(
                        BackupRestoreResult.PostValidationFailure(
                            reason = PostValidationFailureReason.PAYLOAD_SEMANTIC_MISMATCH,
                        ),
                    )
                }
            }

            is BackupExportResult.DatabaseUnsafe -> {
                throw RestoreAbortException(
                    BackupRestoreResult.PostValidationFailure(
                        reason = PostValidationFailureReason.EXPORT_DOMAIN_UNSAFE,
                        detail = exportResult.reason.name,
                    ),
                )
            }

            is BackupExportResult.ReadFailure -> {
                throw RestoreAbortException(
                    BackupRestoreResult.PostValidationFailure(
                        reason = PostValidationFailureReason.EXPORT_SNAPSHOT_FAILED,
                        detail = exportResult.exceptionClass,
                    ),
                )
            }
        }

        val state = snapshot.practiceState
            ?: throw RestoreAbortException(
                BackupRestoreResult.PostValidationFailure(
                    reason = PostValidationFailureReason.EXPORT_SNAPSHOT_FAILED,
                    detail = "missing_practice_state",
                ),
            )

        if (state.isPracticeStarted) {
            val cursorResult = CycleCursorConsistency.analyze(database, state)
            if (cursorResult.kind == CursorConsistencyKind.UNSAFE_CORRUPTION) {
                throw RestoreAbortException(
                    BackupRestoreResult.PostValidationFailure(
                        reason = PostValidationFailureReason.CURSOR_INCONSISTENT,
                        detail = cursorResult.detail,
                    ),
                )
            }
        }
    }

    private suspend fun readTransactionSnapshot(): RoomBackupSnapshot {
        return RoomBackupSnapshot(
            practiceState = database.practiceStateDao().get(),
            scheduleSlots = database.scheduleSlotDao().getAllOrderedByTime(),
            occurrences = database.questionOccurrenceDao().getAllOrderedByPlannedAt(),
            answers = database.answerDao().getAllOrderedByCreatedAt(),
        )
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
