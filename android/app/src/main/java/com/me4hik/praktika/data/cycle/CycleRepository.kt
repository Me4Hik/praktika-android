// 04.08.2026 Cycle Engine cursor by Me4Hik START - Room-транзакции цикла
package com.me4hik.praktika.data.cycle

import android.util.Log
import com.me4hik.praktika.data.backup.write.BackupMutationRequestSink
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.seed.SeedDataValidator
import java.util.concurrent.Callable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CycleRepository(
    private val database: PraktikaDatabase,
    private val timeProvider: TimeProvider,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation sink
    private val backupMutationRequestSink: BackupMutationRequestSink,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    private val scheduleCalculator: ScheduleCalculator = ScheduleCalculator(),
) {
    private val mutex = Mutex()

    suspend fun startPractice(): CycleResult = mutex.withLock {
        val result = runCycleTransaction {
            val now = timeProvider.nowEpochMillis()
            val zoneId = timeProvider.currentZoneId()
            scheduleCalculator.parseZone(zoneId)

            val state = loadPracticeState()
            if (state.isPracticeStarted) {
                throw CycleAlreadyStartedException("Practice has already been started")
            }
            if (state.isPaused) {
                throw CycleCorruptionException("Practice is paused before start")
            }
            if (database.questionOccurrenceDao().count() > 0) {
                throw CycleCorruptionException("Occurrences exist before practice start")
            }

            val slots = loadValidatedSlots()
            validateQuestions()

            val question = database.questionDao().getByCyclePosition(CycleCursor.MIN_POSITION)
                ?: throw CycleCorruptionException("Question position 1 is missing")

            val startSlot = scheduleCalculator.findStartSlot(now, zoneId, slots)
            val availableUntil = scheduleCalculator.calculateAvailableUntil(startSlot, slots)
            val status = if (scheduleCalculator.isWithinSlotMinute(now, startSlot.plannedAtEpochMillis, zoneId)) {
                QuestionOccurrenceStatus.AVAILABLE
            } else {
                QuestionOccurrenceStatus.SCHEDULED
            }

            database.questionOccurrenceDao().insert(
                QuestionOccurrenceEntity(
                    questionId = question.id,
                    questionTextSnapshot = question.text,
                    cycleNumber = 1,
                    cyclePosition = CycleCursor.MIN_POSITION,
                    scheduleSlotIndex = startSlot.slotIndex,
                    plannedAtEpochMillis = startSlot.plannedAtEpochMillis,
                    availableUntilEpochMillis = availableUntil,
                    openedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    status = status,
                    zoneId = zoneId,
                ),
            )

            database.practiceStateDao().update(
                state.copy(
                    isPracticeStarted = true,
                    isPaused = false,
                    practiceStartedAtEpochMillis = now,
                    currentCycleNumber = 1,
                    nextCyclePosition = 2,
                    lastProcessedAtEpochMillis = now,
                    pausedAtEpochMillis = null,
                    activeZoneId = zoneId,
                ),
            )

            validateInvariants(loadValidatedSlots())
            CycleResult.PracticeStarted
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C PRACTICE_STARTED
        if (result is CycleResult.PracticeStarted) {
            requestMutationBackup(BackupRequestReason.PRACTICE_STARTED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        result
    }

    suspend fun reconcile(): CycleResult = mutex.withLock {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C reconcile change signal
        lateinit var outcome: InternalReconcileOutcome
        val result = runCycleTransaction {
            outcome = reconcileWithinTransaction()
            outcome.publicResult
        }
        if (outcome.backedUpStateChanged) {
            requestMutationBackup(BackupRequestReason.RUNTIME_RECONCILED)
        }
        result
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - timezone sync + reconcile
    suspend fun syncEnvironmentAndReconcile(): CycleResult = mutex.withLock {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C timezone + reconcile backup
        lateinit var outcome: InternalReconcileOutcome
        val result = runCycleTransaction {
            outcome = syncEnvironmentAndReconcileWithinTransaction()
            outcome.publicResult
        }
        if (outcome.backedUpStateChanged) {
            requestMutationBackup(BackupRequestReason.RUNTIME_RECONCILED)
        }
        result
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    suspend fun markOccurrenceOpened(expectedOccurrenceId: Long): Boolean = mutex.withLock {
        val changed = runCycleTransaction {
            val now = timeProvider.nowEpochMillis()
            database.questionOccurrenceDao().markOpenedIfNull(expectedOccurrenceId, now) == 1
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C open occurrence backup
        if (changed) {
            requestMutationBackup(BackupRequestReason.PRACTICE_STATE_CHANGED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        changed
    }

    suspend fun getOccurrenceById(id: Long): QuestionOccurrenceEntity? {
        return database.questionOccurrenceDao().getById(id)
    }
    // 06.08.2026 Stage 12 Notifications cursor by Me4Hik END

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C reconcile backup carrier
    private data class InternalReconcileOutcome(
        val publicResult: CycleResult,
        val backedUpStateChanged: Boolean,
    )

    private suspend fun syncEnvironmentAndReconcileWithinTransaction(): InternalReconcileOutcome {
        val state = loadPracticeState()
        val now = timeProvider.nowEpochMillis()
        val systemZoneId = timeProvider.currentZoneId()

        if (!state.isPracticeStarted) {
            val zoneChanged = persistActiveZoneIfChanged(state, systemZoneId)
            return InternalReconcileOutcome(CycleResult.ReconcileNotStarted, zoneChanged)
        }
        if (state.isPaused) {
            val zoneChanged = persistActiveZoneIfChanged(state, systemZoneId)
            return InternalReconcileOutcome(CycleResult.ReconcilePaused, zoneChanged)
        }

        val slots = loadValidatedSlots()
        val zoneId = state.activeZoneId
        scheduleCalculator.parseZone(zoneId)
        var changed = reconcileInternal(state, slots, zoneId, now)
        if (zoneId != systemZoneId) {
            changed = applyTimezoneChange(systemZoneId, now, slots) || changed
            changed = reconcileInternal(loadPracticeState(), loadValidatedSlots(), systemZoneId, now) || changed
        }
        val publicResult = if (changed) {
            validateInvariants(loadValidatedSlots())
            CycleResult.ReconcileChanged
        } else {
            CycleResult.ReconcileNoChanges
        }
        return InternalReconcileOutcome(publicResult, changed)
    }

    private suspend fun persistActiveZoneIfChanged(
        state: PracticeStateEntity,
        systemZoneId: String,
    ): Boolean {
        if (state.activeZoneId == systemZoneId) {
            return false
        }
        database.practiceStateDao().update(state.copy(activeZoneId = systemZoneId))
        return true
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private suspend fun applyTimezoneChange(
        newZoneId: String,
        now: Long,
        slots: List<ScheduleSlotEntity>,
    ): Boolean {
        val state = loadPracticeState()
        database.practiceStateDao().update(state.copy(activeZoneId = newZoneId))
        scheduleCalculator.parseZone(newZoneId)

        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.size != 1) {
            return false
        }
        val current = incomplete.single()
        return when (current.status) {
            QuestionOccurrenceStatus.SCHEDULED -> {
                adjustScheduledOccurrenceForScheduleUpdate(
                    occurrence = current,
                    now = now,
                    zoneId = newZoneId,
                    slots = slots,
                )
                true
            }
            QuestionOccurrenceStatus.AVAILABLE -> {
                adjustAvailableOccurrenceForScheduleUpdate(
                    occurrence = current,
                    now = now,
                    zoneId = newZoneId,
                    slots = slots,
                )
                true
            }
            else -> false
        }
    }

    private suspend fun reconcileWithinTransaction(): InternalReconcileOutcome {
        val state = loadPracticeState()
        if (!state.isPracticeStarted) {
            return InternalReconcileOutcome(CycleResult.ReconcileNotStarted, backedUpStateChanged = false)
        }
        if (state.isPaused) {
            return InternalReconcileOutcome(CycleResult.ReconcilePaused, backedUpStateChanged = false)
        }

        val now = timeProvider.nowEpochMillis()
        val slots = loadValidatedSlots()
        val zoneId = state.activeZoneId
        scheduleCalculator.parseZone(zoneId)

        val changed = reconcileInternal(state, slots, zoneId, now)
        val publicResult = if (changed) {
            validateInvariants(slots)
            CycleResult.ReconcileChanged
        } else {
            CycleResult.ReconcileNoChanges
        }
        return InternalReconcileOutcome(publicResult, changed)
    }

    suspend fun skipAvailableByUser(): CycleResult = mutex.withLock {
        val result = runCycleTransaction {
            skipAvailableByUserInternal(expectedOccurrenceId = null)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C skip backup
        if (result is CycleResult.SkipCompleted) {
            requestMutationBackup(BackupRequestReason.PRACTICE_STATE_CHANGED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        result
    }

    suspend fun skipAvailableByUser(expectedOccurrenceId: Long): CycleResult = mutex.withLock {
        val result = runCycleTransaction {
            skipAvailableByUserInternal(expectedOccurrenceId = expectedOccurrenceId)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C skip backup
        if (result is CycleResult.SkipCompleted) {
            requestMutationBackup(BackupRequestReason.PRACTICE_STATE_CHANGED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        result
    }

    // 05.08.2026 Answer Save cursor by Me4Hik START - атомарное сохранение ответа
    suspend fun saveAnswer(
        expectedOccurrenceId: Long,
        answerText: String,
    ): CycleResult = mutex.withLock {
        val result = runCycleTransaction {
            saveAnswerInternal(
                expectedOccurrenceId = expectedOccurrenceId,
                answerText = answerText,
            )
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C ANSWER_SAVED
        if (result is CycleResult.AnswerSaved) {
            requestMutationBackup(BackupRequestReason.ANSWER_SAVED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        result
    }

    // 06.08.2026 Settings Schedule cursor by Me4Hik START - атомарное обновление расписания
    suspend fun updateSchedule(
        updates: List<ScheduleSlotUpdate>,
    ): ScheduleUpdateResult = mutex.withLock {
        val validatedUpdates = validateScheduleUpdates(updates)
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C schedule overall change oracle
        var backupReason: BackupRequestReason? = null
        val result = runCycleTransaction {
            val now = timeProvider.nowEpochMillis()
            val state = loadPracticeState()
            scheduleCalculator.parseZone(state.activeZoneId)
            val oldSlots = loadValidatedSlots()
            val scheduleChanged = hasSemanticScheduleChange(oldSlots, validatedUpdates)

            var reconcileChanged = false
            if (state.isPracticeStarted && !state.isPaused) {
                reconcileChanged = reconcileInternal(state, oldSlots, state.activeZoneId, now)
            }

            applyStagedSlotUpdates(validatedUpdates)
            val newSlots = loadValidatedSlots()

            var occurrenceAdjustChanged = false
            if (!state.isPracticeStarted || state.isPaused) {
                validateInvariants(newSlots)
            } else {
                val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
                if (incomplete.size != 1) {
                    throw CycleCorruptionException(
                        "Expected exactly one incomplete occurrence during schedule update, found ${incomplete.size}",
                    )
                }

                val current = incomplete.single()
                occurrenceAdjustChanged = when (current.status) {
                    QuestionOccurrenceStatus.SCHEDULED -> {
                        adjustScheduledOccurrenceForScheduleUpdate(
                            occurrence = current,
                            now = now,
                            zoneId = state.activeZoneId,
                            slots = newSlots,
                        )
                    }

                    QuestionOccurrenceStatus.AVAILABLE -> {
                        adjustAvailableOccurrenceForScheduleUpdate(
                            occurrence = current,
                            now = now,
                            zoneId = state.activeZoneId,
                            slots = newSlots,
                        )
                    }

                    else -> throw CycleCorruptionException(
                        "Unexpected incomplete status during schedule update: ${current.status}",
                    )
                }

                validateInvariants(newSlots)
            }

            val overallChanged = scheduleChanged || reconcileChanged || occurrenceAdjustChanged
            if (overallChanged) {
                backupReason = if (scheduleChanged) {
                    BackupRequestReason.SCHEDULE_CHANGED
                } else {
                    BackupRequestReason.RUNTIME_RECONCILED
                }
            }
            ScheduleUpdateResult.Success
        }
        backupReason?.let { requestMutationBackup(it) }
        result
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    private fun validateScheduleUpdates(updates: List<ScheduleSlotUpdate>): List<ScheduleSlotUpdate> {
        if (updates.size != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            throw ScheduleValidationException(ScheduleValidationReason.INVALID_SLOT_COUNT)
        }

        val indices = mutableSetOf<Int>()
        val minutes = mutableSetOf<Int>()
        val normalized = updates.map { update ->
            if (update.slotIndex !in SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE) {
                throw ScheduleValidationException(ScheduleValidationReason.INVALID_SLOT_INDEX)
            }
            if (update.timeOfDayMinutes !in SeedDataValidator.MINUTES_RANGE) {
                throw ScheduleValidationException(ScheduleValidationReason.TIME_OUT_OF_RANGE)
            }
            if (!indices.add(update.slotIndex)) {
                throw ScheduleValidationException(ScheduleValidationReason.DUPLICATE_SLOT_INDEX)
            }
            if (!minutes.add(update.timeOfDayMinutes)) {
                throw ScheduleValidationException(ScheduleValidationReason.DUPLICATE_TIME)
            }
            update
        }

        SeedDataValidator.EXPECTED_SLOT_INDEX_RANGE.forEach { expectedIndex ->
            if (expectedIndex !in indices) {
                throw ScheduleValidationException(ScheduleValidationReason.INVALID_SLOT_INDEX)
            }
        }

        return normalized.sortedBy { it.slotIndex }
    }

    private suspend fun applyStagedSlotUpdates(updates: List<ScheduleSlotUpdate>) {
        val slotDao = database.scheduleSlotDao()
        updates.forEach { update ->
            slotDao.updateTime(
                slotIndex = update.slotIndex,
                timeOfDayMinutes = STAGED_SLOT_MINUTES_BASE + update.slotIndex,
            )
        }
        updates.forEach { update ->
            slotDao.updateTime(
                slotIndex = update.slotIndex,
                timeOfDayMinutes = update.timeOfDayMinutes,
            )
        }
    }

    private suspend fun adjustScheduledOccurrenceForScheduleUpdate(
        occurrence: QuestionOccurrenceEntity,
        now: Long,
        zoneId: String,
        slots: List<ScheduleSlotEntity>,
    ): Boolean {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C occurrence adjust change signal
        val before = occurrence
        val startSlot = scheduleCalculator.findStartSlot(now, zoneId, slots)
        val availableUntil = scheduleCalculator.calculateAvailableUntil(startSlot, slots)
        val status = if (
            scheduleCalculator.isWithinSlotMinute(now, startSlot.plannedAtEpochMillis, zoneId)
        ) {
            QuestionOccurrenceStatus.AVAILABLE
        } else {
            QuestionOccurrenceStatus.SCHEDULED
        }
        val updatedRows = database.questionOccurrenceDao().rescheduleScheduledOccurrence(
            id = occurrence.id,
            scheduleSlotIndex = startSlot.slotIndex,
            plannedAtEpochMillis = startSlot.plannedAtEpochMillis,
            availableUntilEpochMillis = availableUntil,
            zoneId = zoneId,
            status = status,
            completedAtEpochMillis = null,
        )
        if (updatedRows != 1) {
            throw CycleCorruptionException(
                "Failed to reschedule SCHEDULED occurrence ${occurrence.id} during schedule update",
            )
        }
        val after = database.questionOccurrenceDao().getById(occurrence.id)
            ?: throw CycleCorruptionException("Occurrence ${occurrence.id} missing after schedule reschedule")
        return hasOccurrenceBackupPayloadChange(before, after)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    private suspend fun adjustAvailableOccurrenceForScheduleUpdate(
        occurrence: QuestionOccurrenceEntity,
        now: Long,
        zoneId: String,
        slots: List<ScheduleSlotEntity>,
    ): Boolean {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C occurrence adjust change signal
        val before = occurrence
        val nextSlot = scheduleCalculator.findStrictlyNextSlot(
            afterEpochMillis = now,
            zoneId = zoneId,
            slots = slots,
        )
        if (nextSlot.plannedAtEpochMillis <= now) {
            throw CycleInvalidScheduleException(
                "Strictly next slot must be after now during AVAILABLE schedule update",
            )
        }
        val updatedRows = database.questionOccurrenceDao().updateAvailableUntil(
            id = occurrence.id,
            availableUntilEpochMillis = nextSlot.plannedAtEpochMillis,
        )
        if (updatedRows != 1) {
            throw CycleCorruptionException(
                "Failed to update AVAILABLE deadline for ${occurrence.id} during schedule update",
            )
        }
        val after = database.questionOccurrenceDao().getById(occurrence.id)
            ?: throw CycleCorruptionException("Occurrence ${occurrence.id} missing after availableUntil update")
        return hasOccurrenceBackupPayloadChange(before, after)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }
    // 06.08.2026 Settings Schedule cursor by Me4Hik END

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C schedule/occurrence compare helpers
    private fun hasSemanticScheduleChange(
        oldSlots: List<ScheduleSlotEntity>,
        updates: List<ScheduleSlotUpdate>,
    ): Boolean {
        val byIndex = oldSlots.associateBy { it.slotIndex }
        return updates.any { update ->
            byIndex[update.slotIndex]?.timeOfDayMinutes != update.timeOfDayMinutes
        }
    }

    private fun hasOccurrenceBackupPayloadChange(
        before: QuestionOccurrenceEntity,
        after: QuestionOccurrenceEntity,
    ): Boolean {
        return before.copy(id = 0L) != after.copy(id = 0L)
    }

    private fun requestMutationBackup(reason: BackupRequestReason) {
        try {
            backupMutationRequestSink.requestBackup(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ordinary backup enqueue failure must not undo committed mutation.
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private suspend fun saveAnswerInternal(
        expectedOccurrenceId: Long,
        answerText: String,
    ): CycleResult {
        if (expectedOccurrenceId <= 0L) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.EXPECTED_ID_MISMATCH,
                "Expected occurrence id must be positive, got $expectedOccurrenceId",
            )
        }
        if (answerText.isBlank()) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.BLANK,
                "Answer text must not be blank",
            )
        }

        val state = loadPracticeState()
        ensurePracticeActive(state)

        val now = timeProvider.nowEpochMillis()
        val slots = loadValidatedSlots()
        val zoneId = state.activeZoneId

        reconcileInternal(state, slots, zoneId, now)

        val refreshedState = loadPracticeState()
        validateInvariants(slots)

        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.size != 1) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.NO_AVAILABLE_OCCURRENCE,
                "Expected exactly one incomplete occurrence, found ${incomplete.size}",
            )
        }

        val current = incomplete.single()
        if (current.id != expectedOccurrenceId) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.EXPECTED_ID_MISMATCH,
                "Expected occurrence $expectedOccurrenceId but current is ${current.id}",
            )
        }
        if (current.status != QuestionOccurrenceStatus.AVAILABLE) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.NO_AVAILABLE_OCCURRENCE,
                "No active AVAILABLE occurrence to answer",
            )
        }

        val available = current
        if (now >= available.availableUntilEpochMillis) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.WINDOW_EXPIRED,
                "Answer is not allowed after deadline; reconciliation must mark MISSED_BY_TIME first",
            )
        }
        if (database.answerDao().getByOccurrenceId(expectedOccurrenceId) != null) {
            throw CycleAnswerNotAllowedException(
                CycleAnswerNotAllowedReason.ANSWER_ALREADY_EXISTS,
                "Answer already exists for occurrence $expectedOccurrenceId",
            )
        }

        database.answerDao().insert(
            AnswerEntity(
                occurrenceId = expectedOccurrenceId,
                text = answerText,
                createdAtEpochMillis = now,
            ),
        )

        val updatedRows = database.questionOccurrenceDao().updateStatusIfCurrent(
            id = available.id,
            expectedStatus = QuestionOccurrenceStatus.AVAILABLE,
            newStatus = QuestionOccurrenceStatus.ANSWERED,
            completedAtEpochMillis = now,
        )
        if (updatedRows != 1) {
            throw CycleCorruptionException("Failed to mark AVAILABLE occurrence ${available.id} as ANSWERED")
        }

        val nextSlot = scheduleCalculator.findStrictlyNextSlot(now, zoneId, slots)
        var cursorState = refreshedState.copy(
            lastProcessedAtEpochMillis = maxLastProcessed(refreshedState.lastProcessedAtEpochMillis, now),
        )
        cursorState = insertNextOccurrence(
            state = cursorState,
            plannedMoment = nextSlot,
            slots = slots,
            now = now,
        )

        database.practiceStateDao().update(cursorState)
        validateInvariants(slots)
        return CycleResult.AnswerSaved
    }
    // 05.08.2026 Answer Save cursor by Me4Hik END

    private suspend fun skipAvailableByUserInternal(expectedOccurrenceId: Long?): CycleResult {
        val state = loadPracticeState()
        ensurePracticeActive(state)

        val now = timeProvider.nowEpochMillis()
        val slots = loadValidatedSlots()
        val zoneId = state.activeZoneId

        reconcileInternal(state, slots, zoneId, now)

        val refreshedState = loadPracticeState()
        validateInvariants(slots)

        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.size != 1 || incomplete.single().status != QuestionOccurrenceStatus.AVAILABLE) {
            throw CycleSkipNotAllowedException("No active AVAILABLE occurrence to skip")
        }

        val available = incomplete.single()
        if (expectedOccurrenceId != null && available.id != expectedOccurrenceId) {
            throw CycleSkipNotAllowedException(
                "Expected occurrence $expectedOccurrenceId but current is ${available.id}",
            )
        }
        if (now >= available.availableUntilEpochMillis) {
            throw CycleSkipNotAllowedException(
                "Skip is not allowed after deadline; reconciliation must mark MISSED_BY_TIME first",
            )
        }

        val updatedRows = database.questionOccurrenceDao().updateStatusIfCurrent(
            id = available.id,
            expectedStatus = QuestionOccurrenceStatus.AVAILABLE,
            newStatus = QuestionOccurrenceStatus.SKIPPED_BY_USER,
            completedAtEpochMillis = now,
        )
        if (updatedRows != 1) {
            throw CycleCorruptionException("Failed to skip AVAILABLE occurrence ${available.id}")
        }

        val nextSlot = scheduleCalculator.findStrictlyNextSlot(now, zoneId, slots)
        var cursorState = refreshedState.copy(
            lastProcessedAtEpochMillis = maxLastProcessed(refreshedState.lastProcessedAtEpochMillis, now),
        )
        cursorState = insertNextOccurrence(
            state = cursorState,
            plannedMoment = nextSlot,
            slots = slots,
            now = now,
        )

        database.practiceStateDao().update(cursorState)
        validateInvariants(slots)
        return CycleResult.SkipCompleted
    }

    suspend fun pausePractice(): CycleResult = mutex.withLock {
        val result = runCycleTransaction {
            val state = loadPracticeState()
            ensurePracticeActive(state)
            if (state.isPaused) {
                throw CycleAlreadyPausedException("Practice is already paused")
            }

            val now = timeProvider.nowEpochMillis()
            val slots = loadValidatedSlots()
            val zoneId = state.activeZoneId

            reconcileInternal(state, slots, zoneId, now)

            val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
            if (incomplete.size != 1) {
                throw CycleCorruptionException(
                    "Expected exactly one incomplete occurrence before pause, found ${incomplete.size}",
                )
            }

            database.practiceStateDao().update(
                loadPracticeState().copy(
                    isPaused = true,
                    pausedAtEpochMillis = now,
                    lastProcessedAtEpochMillis = maxLastProcessed(
                        loadPracticeState().lastProcessedAtEpochMillis,
                        now,
                    ),
                ),
            )

            validateInvariants(slots)
            CycleResult.PauseEnabled
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C pause backup
        if (result is CycleResult.PauseEnabled) {
            requestMutationBackup(BackupRequestReason.PRACTICE_STATE_CHANGED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        result
    }

    suspend fun resumePractice(): CycleResult = mutex.withLock {
        val result = runCycleTransaction {
            val state = loadPracticeState()
            if (!state.isPracticeStarted) {
                throw CycleNotStartedException("Practice has not been started")
            }
            if (!state.isPaused) {
                throw CycleNotPausedException("Practice is not paused")
            }
            val pausedAt = state.pausedAtEpochMillis
                ?: throw CycleCorruptionException("pausedAtEpochMillis is null while paused")

            val now = timeProvider.nowEpochMillis()
            val slots = loadValidatedSlots()
            val zoneId = state.activeZoneId
            scheduleCalculator.parseZone(zoneId)

            val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
            if (incomplete.size != 1) {
                throw CycleCorruptionException(
                    "Expected exactly one incomplete occurrence before resume, found ${incomplete.size}",
                )
            }

            val current = incomplete.single()
            when (current.status) {
                QuestionOccurrenceStatus.SCHEDULED -> {
                    val startSlot = scheduleCalculator.findStartSlot(now, zoneId, slots)
                    val availableUntil = scheduleCalculator.calculateAvailableUntil(startSlot, slots)
                    val status = if (
                        scheduleCalculator.isWithinSlotMinute(now, startSlot.plannedAtEpochMillis, zoneId)
                    ) {
                        QuestionOccurrenceStatus.AVAILABLE
                    } else {
                        QuestionOccurrenceStatus.SCHEDULED
                    }
                    val updatedRows = database.questionOccurrenceDao().rescheduleScheduledOccurrence(
                        id = current.id,
                        scheduleSlotIndex = startSlot.slotIndex,
                        plannedAtEpochMillis = startSlot.plannedAtEpochMillis,
                        availableUntilEpochMillis = availableUntil,
                        zoneId = zoneId,
                        status = status,
                        completedAtEpochMillis = null,
                    )
                    if (updatedRows != 1) {
                        throw CycleCorruptionException("Failed to reschedule SCHEDULED occurrence ${current.id}")
                    }
                }

                QuestionOccurrenceStatus.AVAILABLE -> {
                    val pauseDuration = if (now < pausedAt) {
                        Log.w(TAG, "Clock moved backward during pause; using zero pause duration")
                        0L
                    } else {
                        now - pausedAt
                    }
                    val newAvailableUntil = safeAdd(current.availableUntilEpochMillis, pauseDuration)
                    val updatedRows = database.questionOccurrenceDao().updateAvailableUntil(
                        id = current.id,
                        availableUntilEpochMillis = newAvailableUntil,
                    )
                    if (updatedRows != 1) {
                        throw CycleCorruptionException("Failed to extend AVAILABLE deadline for ${current.id}")
                    }
                }

                else -> throw CycleCorruptionException(
                    "Unexpected incomplete status during resume: ${current.status}",
                )
            }

            database.practiceStateDao().update(
                state.copy(
                    isPaused = false,
                    pausedAtEpochMillis = null,
                    lastProcessedAtEpochMillis = maxLastProcessed(state.lastProcessedAtEpochMillis, now),
                ),
            )

            validateInvariants(slots)
            CycleResult.PracticeResumed
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C resume backup
        if (result is CycleResult.PracticeResumed) {
            requestMutationBackup(BackupRequestReason.PRACTICE_STATE_CHANGED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        result
    }

    private suspend fun reconcileInternal(
        state: PracticeStateEntity,
        slots: List<ScheduleSlotEntity>,
        zoneId: String,
        now: Long,
    ): Boolean {
        var changed = false
        var cursorState = state
        val (repairedState, cursorRepaired) = repairCursorIfNeeded(state)
        if (cursorRepaired) {
            cursorState = repairedState
            changed = true
        }
        validateInvariants(slots)

        var iterations = 0
        while (iterations++ < MAX_RECONCILE_ITERATIONS) {
            val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
            if (incomplete.isEmpty()) {
                break
            }
            if (incomplete.size > 1) {
                throw CycleCorruptionException(
                    "Expected at most one incomplete occurrence, found ${incomplete.size}",
                )
            }

            val current = incomplete.single()
            when (current.status) {
                QuestionOccurrenceStatus.SCHEDULED -> {
                    when {
                        now < current.plannedAtEpochMillis -> {
                            persistPracticeStateIfNeeded(state, cursorState, changed, now)
                            return changed
                        }

                        now >= current.availableUntilEpochMillis -> {
                            markMissed(current)
                            changed = true
                            cursorState = createFollowingOccurrenceChain(
                                state = cursorState,
                                previous = current,
                                slots = slots,
                                zoneId = zoneId,
                                now = now,
                            )
                        }

                        now >= current.plannedAtEpochMillis -> {
                            val updatedRows = database.questionOccurrenceDao().updateStatusIfCurrent(
                                id = current.id,
                                expectedStatus = QuestionOccurrenceStatus.SCHEDULED,
                                newStatus = QuestionOccurrenceStatus.AVAILABLE,
                                completedAtEpochMillis = null,
                            )
                            if (updatedRows == 1) {
                                changed = true
                            }
                            persistPracticeStateIfNeeded(state, cursorState, changed, now)
                            return changed
                        }
                    }
                }

                QuestionOccurrenceStatus.AVAILABLE -> {
                    when {
                        now < current.availableUntilEpochMillis -> {
                            persistPracticeStateIfNeeded(state, cursorState, changed, now)
                            return changed
                        }

                        now >= current.availableUntilEpochMillis -> {
                            markMissed(current)
                            changed = true
                            cursorState = createFollowingOccurrenceChain(
                                state = cursorState,
                                previous = current,
                                slots = slots,
                                zoneId = zoneId,
                                now = now,
                            )
                        }
                    }
                }

                else -> throw CycleCorruptionException(
                    "Unexpected incomplete status during reconcile: ${current.status}",
                )
            }
        }

        if (iterations >= MAX_RECONCILE_ITERATIONS) {
            throw CycleInvalidScheduleException(
                "Reconciliation iteration limit reached; schedule state may be corrupted",
            )
        }

        if (changed && hasLogicalPracticeStateChange(state, cursorState)) {
            persistPracticeState(cursorState, now)
        }
        return changed
    }

    private suspend fun persistPracticeStateIfNeeded(
        originalState: PracticeStateEntity,
        cursorState: PracticeStateEntity,
        changed: Boolean,
        now: Long,
    ) {
        if (changed && hasLogicalPracticeStateChange(originalState, cursorState)) {
            persistPracticeState(cursorState, now)
        }
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - in-place cycle cursor recovery
    private suspend fun repairCursorIfNeeded(
        state: PracticeStateEntity,
    ): Pair<PracticeStateEntity, Boolean> {
        val analysis = CycleCursorConsistency.analyze(database, state)
        return when (analysis.kind) {
            CursorConsistencyKind.CONSISTENT -> state to false
            CursorConsistencyKind.UNSAFE_CORRUPTION -> {
                recordCursorRecoveryRejected(analysis)
                throw CycleCorruptionException(
                    analysis.detail ?: "Cursor recovery rejected: ${analysis.reason}",
                )
            }
            CursorConsistencyKind.STALE_CURSOR_RECOVERABLE -> {
                val repaired = analysis.repairedState ?: state
                recordCursorRecovered(analysis)
                repaired to true
            }
        }
    }

    private fun recordCursorRecovered(analysis: CursorConsistencyResult) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        val before = analysis.cursorBefore ?: return
        val after = analysis.cursorAfter ?: return
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.APP,
            name = "cycle_cursor_recovered",
            metadata = mapOf(
                "cycle" to before.cycleNumber.toString(),
                "cursor_before" to before.cyclePosition.toString(),
                "cursor_after" to after.cyclePosition.toString(),
                "existing_position_count" to analysis.existingPositionCount.toString(),
                "first_missing_position" to (analysis.firstMissingPosition?.toString() ?: ""),
                "recovery_reason" to "stale_cursor_existing_row",
            ),
        )
    }

    private fun recordCursorRecoveryRejected(analysis: CursorConsistencyResult) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        val before = analysis.cursorBefore
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.APP,
            name = "cycle_cursor_recovery_rejected",
            metadata = buildMap {
                put("reason_enum", analysis.reason?.name ?: "UNKNOWN")
                put("cycle", (before?.cycleNumber ?: 0).toString())
                put("cursor", (before?.cyclePosition ?: 0).toString())
                if (!analysis.detail.isNullOrBlank()) {
                    put("detail", analysis.detail.take(200))
                }
            },
        )
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private suspend fun persistPracticeState(state: PracticeStateEntity, now: Long) {
        database.practiceStateDao().update(
            state.copy(
                lastProcessedAtEpochMillis = maxLastProcessed(state.lastProcessedAtEpochMillis, now),
            ),
        )
    }

    private fun hasLogicalPracticeStateChange(
        before: PracticeStateEntity,
        after: PracticeStateEntity,
    ): Boolean {
        return before.copy(lastProcessedAtEpochMillis = null) != after.copy(lastProcessedAtEpochMillis = null)
    }

    private suspend fun createFollowingOccurrenceChain(
        state: PracticeStateEntity,
        previous: QuestionOccurrenceEntity,
        slots: List<ScheduleSlotEntity>,
        zoneId: String,
        now: Long,
    ): PracticeStateEntity {
        var cursorState = state
        var previousOccurrence = previous
        var chainIterations = 0

        while (chainIterations++ < MAX_RECONCILE_ITERATIONS) {
            val cycleNumber = cursorState.currentCycleNumber
            val cyclePosition = cursorState.nextCyclePosition
            val plannedMoment = nextPlannedMomentAfterTerminal(previousOccurrence, slots, zoneId)
            cursorState = insertNextOccurrence(
                state = cursorState,
                plannedMoment = plannedMoment,
                slots = slots,
                now = now,
            )
            val created = database.questionOccurrenceDao().getByCycleAndPosition(cycleNumber, cyclePosition)
                ?: throw CycleCorruptionException(
                    "Created occurrence ($cycleNumber, $cyclePosition) is missing",
                )
            if (created.status != QuestionOccurrenceStatus.MISSED_BY_TIME) {
                return cursorState
            }
            previousOccurrence = created
        }

        throw CycleInvalidScheduleException(
            "Occurrence chain iteration limit reached while catching up missed slots",
        )
    }

    private fun nextPlannedMomentAfterTerminal(
        previous: QuestionOccurrenceEntity,
        slots: List<ScheduleSlotEntity>,
        zoneId: String,
    ): SlotMoment {
        return if (isPauseShifted(previous, slots)) {
            scheduleCalculator.findStrictlyNextSlot(
                afterEpochMillis = previous.availableUntilEpochMillis,
                zoneId = zoneId,
                slots = slots,
            )
        } else {
            scheduleCalculator.resolveSlotMoment(
                plannedAtEpochMillis = previous.availableUntilEpochMillis,
                zoneId = zoneId,
                slots = slots,
            )
        }
    }

    private suspend fun insertNextOccurrence(
        state: PracticeStateEntity,
        plannedMoment: SlotMoment,
        slots: List<ScheduleSlotEntity>,
        now: Long,
    ): PracticeStateEntity {
        val cycleNumber = state.currentCycleNumber
        val cyclePosition = state.nextCyclePosition

        val existing = database.questionOccurrenceDao().getByCycleAndPosition(cycleNumber, cyclePosition)
        if (existing != null) {
            return adoptExistingOccurrence(state, existing)
        }

        val question = database.questionDao().getByCyclePosition(cyclePosition)
            ?: throw CycleCorruptionException("Question for position $cyclePosition is missing")

        val availableUntil = scheduleCalculator.calculateAvailableUntil(plannedMoment, slots)
        val status = determineStatusOnCreate(now, plannedMoment.plannedAtEpochMillis, availableUntil)

        database.questionOccurrenceDao().insert(
            QuestionOccurrenceEntity(
                questionId = question.id,
                questionTextSnapshot = question.text,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                scheduleSlotIndex = plannedMoment.slotIndex,
                plannedAtEpochMillis = plannedMoment.plannedAtEpochMillis,
                availableUntilEpochMillis = availableUntil,
                openedAtEpochMillis = null,
                completedAtEpochMillis = if (status == QuestionOccurrenceStatus.MISSED_BY_TIME) {
                    availableUntil
                } else {
                    null
                },
                status = status,
                zoneId = plannedMoment.zoneId,
            ),
        )

        val advanced = CycleCursor(cycleNumber, cyclePosition).advance()
        return state.copy(
            currentCycleNumber = advanced.cycleNumber,
            nextCyclePosition = advanced.cyclePosition,
        )
    }

    private suspend fun adoptExistingOccurrence(
        state: PracticeStateEntity,
        existing: QuestionOccurrenceEntity,
    ): PracticeStateEntity {
        val validation = CycleCursorConsistency.validateAdoptedRow(database, existing)
        if (validation.kind == CursorConsistencyKind.UNSAFE_CORRUPTION) {
            recordCursorRecoveryRejected(validation)
            throw CycleCorruptionException(
                validation.detail ?: "Cannot adopt existing occurrence (${existing.cycleNumber},${existing.cyclePosition})",
            )
        }
        val advanced = CycleCursor(existing.cycleNumber, existing.cyclePosition).advance()
        return state.copy(
            currentCycleNumber = advanced.cycleNumber,
            nextCyclePosition = advanced.cyclePosition,
        )
    }

    private suspend fun markMissed(occurrence: QuestionOccurrenceEntity) {
        val updatedRows = database.questionOccurrenceDao().updateStatusIfCurrent(
            id = occurrence.id,
            expectedStatus = occurrence.status,
            newStatus = QuestionOccurrenceStatus.MISSED_BY_TIME,
            completedAtEpochMillis = occurrence.availableUntilEpochMillis,
        )
        if (updatedRows != 1) {
            throw CycleCorruptionException("Failed to mark occurrence ${occurrence.id} as MISSED_BY_TIME")
        }
    }

    private fun determineStatusOnCreate(
        now: Long,
        plannedAtEpochMillis: Long,
        availableUntilEpochMillis: Long,
    ): QuestionOccurrenceStatus {
        return when {
            now >= availableUntilEpochMillis -> QuestionOccurrenceStatus.MISSED_BY_TIME
            now >= plannedAtEpochMillis -> QuestionOccurrenceStatus.AVAILABLE
            else -> QuestionOccurrenceStatus.SCHEDULED
        }
    }

    private fun isPauseShifted(
        occurrence: QuestionOccurrenceEntity,
        slots: List<ScheduleSlotEntity>,
    ): Boolean {
        val plannedMoment = SlotMoment(
            slotIndex = occurrence.scheduleSlotIndex,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
            zoneId = occurrence.zoneId,
        )
        val standardUntil = scheduleCalculator.calculateAvailableUntil(plannedMoment, slots)
        return occurrence.availableUntilEpochMillis != standardUntil
    }

    private suspend fun validateInvariants(slots: List<ScheduleSlotEntity>) {
        loadPracticeState()
        validateQuestions()
        scheduleCalculator.validateAndSort(slots)

        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.size > 1) {
            throw CycleCorruptionException(
                "Invariant violated: ${incomplete.size} incomplete occurrences",
            )
        }

        val availableCount = database.questionOccurrenceDao()
            .countByStatus(QuestionOccurrenceStatus.AVAILABLE)
        if (availableCount > 1) {
            throw CycleCorruptionException(
                "Invariant violated: $availableCount AVAILABLE occurrences",
            )
        }
    }

    private suspend fun validateQuestions() {
        val questions = database.questionDao().getAllOrderedByCyclePosition()
        if (questions.size != SeedDataValidator.EXPECTED_QUESTION_COUNT) {
            throw CycleCorruptionException(
                "Expected ${SeedDataValidator.EXPECTED_QUESTION_COUNT} questions, found ${questions.size}",
            )
        }
        val positions = questions.map { it.cyclePosition }.toSet()
        if (positions != (CycleCursor.MIN_POSITION..CycleCursor.MAX_POSITION).toSet()) {
            throw CycleCorruptionException("Question cycle positions must cover 1..21")
        }
    }

    private suspend fun loadValidatedSlots(): List<ScheduleSlotEntity> {
        val slots = database.scheduleSlotDao().getAllOrderedByTime()
        if (slots.size != SeedDataValidator.EXPECTED_SLOT_COUNT) {
            throw CycleCorruptionException(
                "Expected ${SeedDataValidator.EXPECTED_SLOT_COUNT} schedule slots, found ${slots.size}",
            )
        }
        return scheduleCalculator.validateAndSort(slots)
    }

    private suspend fun loadPracticeState(): PracticeStateEntity {
        return database.practiceStateDao().get()
            ?: throw CycleCorruptionException("PracticeState row is missing")
    }

    private fun ensurePracticeActive(state: PracticeStateEntity) {
        if (!state.isPracticeStarted) {
            throw CycleNotStartedException("Practice has not been started")
        }
        if (state.isPaused) {
            throw CycleAlreadyPausedException("Practice is paused")
        }
    }

    private fun maxLastProcessed(previous: Long?, now: Long): Long {
        return maxOf(previous ?: now, now)
    }

    private fun safeAdd(value: Long, delta: Long): Long {
        return try {
            Math.addExact(value, delta)
        } catch (exception: ArithmeticException) {
            throw CycleClockException("availableUntil overflow while extending pause deadline")
        }
    }

    private suspend fun <T> runCycleTransaction(block: suspend () -> T): T {
        return database.runInTransaction(
            Callable {
                runBlocking {
                    block()
                }
            },
        )
    }

    private companion object {
        const val TAG = "CycleRepository"
        const val MAX_RECONCILE_ITERATIONS = 10_000
        // 06.08.2026 Settings Schedule cursor by Me4Hik START - staged minutes вне 0..1439
        const val STAGED_SLOT_MINUTES_BASE = 10_000
        // 06.08.2026 Settings Schedule cursor by Me4Hik END
    }
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
