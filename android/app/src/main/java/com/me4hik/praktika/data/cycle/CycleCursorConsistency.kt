// 10.08.2026 Post-release fixes cursor by Me4Hik START - in-place cycle cursor recovery
package com.me4hik.praktika.data.cycle

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus

enum class CursorConsistencyKind {
    CONSISTENT,
    STALE_CURSOR_RECOVERABLE,
    UNSAFE_CORRUPTION,
}

enum class CursorRecoveryRejectionReason {
    MULTIPLE_INCOMPLETE,
    QUESTION_MAPPING_MISMATCH,
    INVALID_TERMINAL_ROW,
    CYCLE_NOT_STARTED,
}

data class CursorConsistencyResult(
    val kind: CursorConsistencyKind,
    val reason: CursorRecoveryRejectionReason? = null,
    val detail: String? = null,
    val cursorBefore: CycleCursor? = null,
    val cursorAfter: CycleCursor? = null,
    val firstMissingPosition: Int? = null,
    val existingPositionCount: Int = 0,
    val repairedState: PracticeStateEntity? = null,
)

internal object CycleCursorConsistency {
    suspend fun analyze(
        database: PraktikaDatabase,
        state: PracticeStateEntity,
    ): CursorConsistencyResult {
        if (!state.isPracticeStarted) {
            return CursorConsistencyResult(kind = CursorConsistencyKind.CONSISTENT)
        }

        val cycleNumber = state.currentCycleNumber
        val nextPosition = state.nextCyclePosition
        val cursorBefore = CycleCursor(cycleNumber, nextPosition)
        val existingAtCursor = database.questionOccurrenceDao()
            .getByCycleAndPosition(cycleNumber, nextPosition)

        if (existingAtCursor == null) {
            return CursorConsistencyResult(
                kind = CursorConsistencyKind.CONSISTENT,
                cursorBefore = cursorBefore,
                existingPositionCount = countExistingInCycle(database, cycleNumber),
            )
        }

        val incomplete = database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.size > 1) {
            return unsafe(
                reason = CursorRecoveryRejectionReason.MULTIPLE_INCOMPLETE,
                detail = "Expected at most one incomplete occurrence, found ${incomplete.size}",
                cursorBefore = cursorBefore,
                existingPositionCount = countExistingInCycle(database, cycleNumber),
            )
        }

        var cursor = cursorBefore
        var firstMissingPosition: Int? = null
        var walkedExistingCount = 0

        while (true) {
            val row = database.questionOccurrenceDao()
                .getByCycleAndPosition(cursor.cycleNumber, cursor.cyclePosition)
                ?: break

            walkedExistingCount++
            val mappingError = validateQuestionMapping(database, row)
            if (mappingError != null) {
                return unsafe(
                    reason = CursorRecoveryRejectionReason.QUESTION_MAPPING_MISMATCH,
                    detail = mappingError,
                    cursorBefore = cursorBefore,
                    existingPositionCount = walkedExistingCount,
                )
            }

            val terminalError = validateTerminalRow(database, row)
            if (terminalError != null) {
                return unsafe(
                    reason = CursorRecoveryRejectionReason.INVALID_TERMINAL_ROW,
                    detail = terminalError,
                    cursorBefore = cursorBefore,
                    existingPositionCount = walkedExistingCount,
                )
            }

            cursor = cursor.advance()
        }

        firstMissingPosition = cursor.cyclePosition
        val repairedState = state.copy(
            currentCycleNumber = cursor.cycleNumber,
            nextCyclePosition = cursor.cyclePosition,
        )

        return CursorConsistencyResult(
            kind = CursorConsistencyKind.STALE_CURSOR_RECOVERABLE,
            cursorBefore = cursorBefore,
            cursorAfter = cursor,
            firstMissingPosition = firstMissingPosition,
            existingPositionCount = walkedExistingCount,
            repairedState = repairedState,
        )
    }

    suspend fun validateAdoptedRow(
        database: PraktikaDatabase,
        row: QuestionOccurrenceEntity,
    ): CursorConsistencyResult {
        validateQuestionMapping(database, row)?.let { detail ->
            return unsafe(
                reason = CursorRecoveryRejectionReason.QUESTION_MAPPING_MISMATCH,
                detail = detail,
                cursorBefore = CycleCursor(row.cycleNumber, row.cyclePosition),
            )
        }
        validateTerminalRow(database, row)?.let { detail ->
            return unsafe(
                reason = CursorRecoveryRejectionReason.INVALID_TERMINAL_ROW,
                detail = detail,
                cursorBefore = CycleCursor(row.cycleNumber, row.cyclePosition),
            )
        }
        return CursorConsistencyResult(kind = CursorConsistencyKind.CONSISTENT)
    }

    private suspend fun validateQuestionMapping(
        database: PraktikaDatabase,
        row: QuestionOccurrenceEntity,
    ): String? {
        val expected = database.questionDao().getByCyclePosition(row.cyclePosition)
            ?: return "Question for position ${row.cyclePosition} is missing"
        return if (expected.id != row.questionId) {
            "Occurrence (${row.cycleNumber},${row.cyclePosition}) questionId=${row.questionId} " +
                "does not match expected questionId=${expected.id}"
        } else {
            null
        }
    }

    private suspend fun validateTerminalRow(
        database: PraktikaDatabase,
        row: QuestionOccurrenceEntity,
    ): String? {
        return when (row.status) {
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - align ANSWERED invariant with intentional answer deletion
            // ANSWERED without AnswerEntity is legal because archive deletion removes text
            // but intentionally preserves occurrence history.
            QuestionOccurrenceStatus.ANSWERED -> {
                if (row.completedAtEpochMillis == null) {
                    "ANSWERED occurrence ${row.id} is missing completedAtEpochMillis"
                } else {
                    null
                }
            }
            // 10.08.2026 Post-release fixes cursor by Me4Hik END

            QuestionOccurrenceStatus.MISSED_BY_TIME,
            QuestionOccurrenceStatus.SKIPPED_BY_USER,
            -> {
                if (row.completedAtEpochMillis == null) {
                    "${row.status} occurrence ${row.id} is missing completedAtEpochMillis"
                } else {
                    null
                }
            }

            QuestionOccurrenceStatus.SCHEDULED,
            QuestionOccurrenceStatus.AVAILABLE,
            -> null
        }
    }

    private suspend fun countExistingInCycle(
        database: PraktikaDatabase,
        cycleNumber: Int,
    ): Int {
        return database.questionOccurrenceDao()
            .getAllOrderedByPlannedAt()
            .count { it.cycleNumber == cycleNumber }
    }

    private fun unsafe(
        reason: CursorRecoveryRejectionReason,
        detail: String,
        cursorBefore: CycleCursor,
        existingPositionCount: Int = 0,
    ): CursorConsistencyResult {
        return CursorConsistencyResult(
            kind = CursorConsistencyKind.UNSAFE_CORRUPTION,
            reason = reason,
            detail = detail,
            cursorBefore = cursorBefore,
            existingPositionCount = existingPositionCount,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
