// 05.08.2026 Main Screen cursor by Me4Hik START - read-слой практики для UI
// 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik START - транзакционно согласованный snapshot
package com.me4hik.praktika.data.read

import androidx.room.withTransaction
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

interface PracticeReadRepository {
    fun observeSnapshot(): Flow<PracticeReadSnapshot>
}

data class PracticeReadSnapshot(
    val practiceState: PracticeStateEntity,
    val incompleteOccurrence: QuestionOccurrenceEntity?,
)

class RoomPracticeReadRepository(
    private val database: PraktikaDatabase,
) : PracticeReadRepository {

    override fun observeSnapshot(): Flow<PracticeReadSnapshot> {
        return database.invalidationTracker
            .createFlow(
                PRACTICE_STATE_TABLE,
                QUESTION_OCCURRENCES_TABLE,
                emitInitialState = true,
            )
            .mapLatest { readConsistentSnapshot() }
            .distinctUntilChanged()
    }

    private suspend fun readConsistentSnapshot(): PracticeReadSnapshot {
        return database.withTransaction {
            val state = database.practiceStateDao().get()
            val incompleteList = database.questionOccurrenceDao().getIncompleteOrdered()
            buildSnapshot(state, incompleteList)
        }
    }

    private fun buildSnapshot(
        state: PracticeStateEntity?,
        incompleteList: List<QuestionOccurrenceEntity>,
    ): PracticeReadSnapshot {
        if (state == null) {
            throw CycleCorruptionException("PracticeState row id=1 is missing after seed")
        }
        if (incompleteList.size > 1) {
            throw CycleCorruptionException(
                "Expected at most one incomplete occurrence, found ${incompleteList.size}",
            )
        }
        val incomplete = incompleteList.singleOrNull()
        if (!state.isPracticeStarted) {
            if (incomplete != null) {
                throw CycleCorruptionException(
                    "Incomplete occurrence exists while practice is not started",
                )
            }
            return PracticeReadSnapshot(state, null)
        }
        if (incomplete == null) {
            throw CycleCorruptionException(
                "Practice is started but no incomplete occurrence exists",
            )
        }
        return PracticeReadSnapshot(state, incomplete)
    }

    private companion object {
        const val PRACTICE_STATE_TABLE = "practice_state"
        const val QUESTION_OCCURRENCES_TABLE = "question_occurrences"
    }
}
// 05.08.2026 Atomic Practice Snapshot cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
