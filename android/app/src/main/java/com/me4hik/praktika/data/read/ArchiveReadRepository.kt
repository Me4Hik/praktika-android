// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik START - read repository архива
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - range observe API
package com.me4hik.praktika.data.read

import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.model.AnswerArchiveRow
import kotlinx.coroutines.flow.Flow
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
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
// 07.08.2026 Stage 14 Archive Layer cursor by Me4Hik END
