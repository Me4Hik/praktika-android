// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik START - read repository архива
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - range observe API
// PROMPT 115 — mixed history events (terminal + defer) without changing answer-only APIs
// 01.10.2026 Archive T1 incomplete defer filter cursor by Me4Hik - defer rows filtered in DeferEventDao
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik START - units instead of flat events
package com.me4hik.praktika.data.read

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.model.AnswerArchiveRow
import com.me4hik.praktika.data.local.model.ArchiveDeferEventRow
import com.me4hik.praktika.data.local.model.ArchiveTerminalOccurrenceRow
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface ArchiveReadRepository {
    fun observeEntries(): Flow<List<ArchiveEntry>>

    fun observeEntriesInRange(
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ): Flow<List<ArchiveEntry>>

    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - observe API по questionId
    fun observeEntriesForQuestion(questionId: Int): Flow<List<ArchiveEntry>>
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

    fun observeOccurrenceHistoryForQuestion(questionId: Int): Flow<List<ArchiveOccurrenceUnit>>

    fun observeAllOccurrenceHistory(): Flow<List<ArchiveOccurrenceUnit>>
}

data class ArchiveEntry(
    val answerId: Long,
    val occurrenceId: Long,
    val questionId: Int,
    val questionText: String,
    val answerText: String,
    val answeredAtEpochMillis: Long,
    val plannedAtEpochMillis: Long,
    val cycleNumber: Int,
    val cyclePosition: Int,
)

class RoomArchiveReadRepository(
    private val database: PraktikaDatabase,
) : ArchiveReadRepository {

    override fun observeEntries(): Flow<List<ArchiveEntry>> {
        return database.answerDao()
            .observeArchiveEntries()
            .map { rows -> rows.map { it.toArchiveEntry() } }
            .distinctUntilChanged()
    }

    override fun observeEntriesInRange(
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ): Flow<List<ArchiveEntry>> {
        return database.answerDao()
            .observeArchiveEntriesInRange(startInclusiveEpochMillis, endExclusiveEpochMillis)
            .map { rows -> rows.map { it.toArchiveEntry() } }
            .distinctUntilChanged()
    }

    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik START - repository observe по questionId
    override fun observeEntriesForQuestion(questionId: Int): Flow<List<ArchiveEntry>> {
        return database.answerDao()
            .observeArchiveEntriesForQuestion(questionId)
            .map { rows -> rows.map { it.toArchiveEntry() } }
            .distinctUntilChanged()
    }
    // 07.08.2026 Stage 16 Archive By Question cursor by Me4Hik END

    override fun observeOccurrenceHistoryForQuestion(questionId: Int): Flow<List<ArchiveOccurrenceUnit>> {
        return combine(
            database.questionOccurrenceDao().observeTerminalArchiveRowsForQuestion(questionId),
            database.deferEventDao().observeArchiveDeferRowsForQuestion(questionId),
        ) { terminals, defers ->
            buildOccurrenceUnits(terminals, defers)
        }.distinctUntilChanged()
    }

    override fun observeAllOccurrenceHistory(): Flow<List<ArchiveOccurrenceUnit>> {
        return combine(
            database.questionOccurrenceDao().observeTerminalArchiveRows(),
            database.deferEventDao().observeArchiveDeferRows(),
        ) { terminals, defers ->
            buildOccurrenceUnits(terminals, defers)
        }.distinctUntilChanged()
    }

    private fun AnswerArchiveRow.toArchiveEntry(): ArchiveEntry {
        return ArchiveEntry(
            answerId = answerId,
            occurrenceId = occurrenceId,
            questionId = questionId,
            questionText = questionText,
            answerText = answerText,
            answeredAtEpochMillis = createdAtEpochMillis,
            plannedAtEpochMillis = plannedAtEpochMillis,
            cycleNumber = cycleNumber,
            cyclePosition = cyclePosition,
        )
    }

    companion object {
        internal fun buildOccurrenceUnits(
            terminals: List<ArchiveTerminalOccurrenceRow>,
            defers: List<ArchiveDeferEventRow>,
        ): List<ArchiveOccurrenceUnit> {
            val defersByOccurrenceId = defers
                .groupBy { it.occurrenceId }
                .mapValues { (_, rows) ->
                    rows
                        .sortedWith(
                            compareBy<ArchiveDeferEventRow> { it.occurredAtEpochMillis }
                                .thenBy { it.deferEventId },
                        )
                        .map { it.toDeferDetail() }
                }
            val units = ArrayList<ArchiveOccurrenceUnit>(terminals.size)
            terminals.mapNotNullTo(units) { row ->
                row.toOccurrenceUnitOrNull(
                    deferEvents = defersByOccurrenceId[row.occurrenceId].orEmpty(),
                )
            }
            return units.sortedWith(OCCURRENCE_UNIT_COMPARATOR)
        }

        private val OCCURRENCE_UNIT_COMPARATOR =
            compareBy<ArchiveOccurrenceUnit> { it.eventAtEpochMillis }
                .thenBy { it.occurrenceId }

        private fun ArchiveTerminalOccurrenceRow.toOccurrenceUnitOrNull(
            deferEvents: List<ArchiveOccurrenceDeferEvent>,
        ): ArchiveOccurrenceUnit? {
            val completedAt = completedAtEpochMillis ?: return null
            return when (status) {
                QuestionOccurrenceStatus.ANSWERED -> {
                    val hasAnswer = answerId != null
                    ArchiveOccurrenceUnit(
                        stableKey = if (hasAnswer) {
                            "a:$answerId"
                        } else {
                            "o:$occurrenceId:answered"
                        },
                        occurrenceId = occurrenceId,
                        questionId = questionId,
                        questionTextSnapshot = questionTextSnapshot,
                        cycleNumber = cycleNumber,
                        cyclePosition = cyclePosition,
                        outcome = ArchiveOccurrenceOutcome.ANSWERED,
                        eventAtEpochMillis = answerCreatedAtEpochMillis ?: completedAt,
                        answerId = answerId,
                        answerText = answerText,
                        deferEvents = deferEvents,
                    )
                }
                QuestionOccurrenceStatus.SKIPPED_BY_USER -> ArchiveOccurrenceUnit(
                    stableKey = "o:$occurrenceId:rejected",
                    occurrenceId = occurrenceId,
                    questionId = questionId,
                    questionTextSnapshot = questionTextSnapshot,
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    outcome = ArchiveOccurrenceOutcome.REJECTED,
                    eventAtEpochMillis = completedAt,
                    answerId = null,
                    answerText = null,
                    deferEvents = deferEvents,
                )
                QuestionOccurrenceStatus.MISSED_BY_TIME -> ArchiveOccurrenceUnit(
                    stableKey = "o:$occurrenceId:missed",
                    occurrenceId = occurrenceId,
                    questionId = questionId,
                    questionTextSnapshot = questionTextSnapshot,
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    outcome = ArchiveOccurrenceOutcome.MISSED,
                    eventAtEpochMillis = completedAt,
                    answerId = null,
                    answerText = null,
                    deferEvents = deferEvents,
                )
                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> null
            }
        }

        private fun ArchiveDeferEventRow.toDeferDetail(): ArchiveOccurrenceDeferEvent {
            return ArchiveOccurrenceDeferEvent(
                deferEventId = deferEventId,
                occurredAtEpochMillis = occurredAtEpochMillis,
                deferredUntilEpochMillis = deferredUntilEpochMillis,
                durationMinutes = durationMinutes,
            )
        }
    }
}
// 01.10.2026 Archive T2 occurrence read model cursor by Me4Hik END
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik END
