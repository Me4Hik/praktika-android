// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik START - read repository архива
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - range observe API
// PROMPT 115 — mixed history events (terminal + defer) without changing answer-only APIs
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

    fun observeHistoryForQuestion(questionId: Int): Flow<List<ArchiveHistoryEvent>>

    fun observeAllHistoryEvents(): Flow<List<ArchiveHistoryEvent>>
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

    override fun observeHistoryForQuestion(questionId: Int): Flow<List<ArchiveHistoryEvent>> {
        return combine(
            database.questionOccurrenceDao().observeTerminalArchiveRowsForQuestion(questionId),
            database.deferEventDao().observeArchiveDeferRowsForQuestion(questionId),
        ) { terminals, defers ->
            mergeHistoryEvents(terminals, defers)
        }.distinctUntilChanged()
    }

    override fun observeAllHistoryEvents(): Flow<List<ArchiveHistoryEvent>> {
        return combine(
            database.questionOccurrenceDao().observeTerminalArchiveRows(),
            database.deferEventDao().observeArchiveDeferRows(),
        ) { terminals, defers ->
            mergeHistoryEvents(terminals, defers)
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
        internal fun mergeHistoryEvents(
            terminals: List<ArchiveTerminalOccurrenceRow>,
            defers: List<ArchiveDeferEventRow>,
        ): List<ArchiveHistoryEvent> {
            val events = ArrayList<ArchiveHistoryEvent>(terminals.size + defers.size)
            terminals.mapNotNullTo(events) { it.toHistoryEventOrNull() }
            defers.mapTo(events) { it.toDeferredEvent() }
            return events.sortedWith(HISTORY_EVENT_COMPARATOR)
        }

        private val HISTORY_EVENT_COMPARATOR = compareBy<ArchiveHistoryEvent> { it.eventAtEpochMillis }
            .thenBy { kindTieRank(it) }
            .thenBy { it.stableKey }

        /** Deferred before terminal outcome when timestamps collide. */
        private fun kindTieRank(event: ArchiveHistoryEvent): Int {
            return when (event) {
                is ArchiveHistoryEvent.Deferred -> 0
                is ArchiveHistoryEvent.Answer,
                is ArchiveHistoryEvent.Rejected,
                is ArchiveHistoryEvent.Missed,
                -> 1
            }
        }

        private fun ArchiveTerminalOccurrenceRow.toHistoryEventOrNull(): ArchiveHistoryEvent? {
            val completedAt = completedAtEpochMillis ?: return null
            return when (status) {
                QuestionOccurrenceStatus.ANSWERED -> {
                    val hasAnswer = answerId != null
                    ArchiveHistoryEvent.Answer(
                        stableKey = if (hasAnswer) {
                            "a:$answerId"
                        } else {
                            "o:$occurrenceId:answered"
                        },
                        questionId = questionId,
                        occurrenceId = occurrenceId,
                        questionTextSnapshot = questionTextSnapshot,
                        cycleNumber = cycleNumber,
                        cyclePosition = cyclePosition,
                        eventAtEpochMillis = answerCreatedAtEpochMillis ?: completedAt,
                        answerId = answerId,
                        answerText = answerText,
                    )
                }
                QuestionOccurrenceStatus.SKIPPED_BY_USER -> ArchiveHistoryEvent.Rejected(
                    stableKey = "o:$occurrenceId:rejected",
                    questionId = questionId,
                    occurrenceId = occurrenceId,
                    questionTextSnapshot = questionTextSnapshot,
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    eventAtEpochMillis = completedAt,
                )
                QuestionOccurrenceStatus.MISSED_BY_TIME -> ArchiveHistoryEvent.Missed(
                    stableKey = "o:$occurrenceId:missed",
                    questionId = questionId,
                    occurrenceId = occurrenceId,
                    questionTextSnapshot = questionTextSnapshot,
                    cycleNumber = cycleNumber,
                    cyclePosition = cyclePosition,
                    eventAtEpochMillis = completedAt,
                )
                QuestionOccurrenceStatus.SCHEDULED,
                QuestionOccurrenceStatus.AVAILABLE,
                -> null
            }
        }

        private fun ArchiveDeferEventRow.toDeferredEvent(): ArchiveHistoryEvent.Deferred {
            return ArchiveHistoryEvent.Deferred(
                stableKey = "d:$deferEventId",
                questionId = questionId,
                occurrenceId = occurrenceId,
                questionTextSnapshot = questionTextSnapshot,
                cycleNumber = cycleNumber,
                cyclePosition = cyclePosition,
                eventAtEpochMillis = occurredAtEpochMillis,
                deferEventId = deferEventId,
                durationMinutes = durationMinutes,
                deferredUntilEpochMillis = deferredUntilEpochMillis,
            )
        }
    }
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik END
