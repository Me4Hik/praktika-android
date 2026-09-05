// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - single-transaction Room snapshot reader
package com.me4hik.praktika.data.backup.export

import androidx.room.withTransaction
import com.me4hik.praktika.data.local.PraktikaDatabase

class RoomBackupSnapshotReader(
    private val database: PraktikaDatabase,
) {
    internal suspend fun readSnapshot(): RoomBackupSnapshot {
        return database.withTransaction {
            RoomBackupSnapshot(
                practiceState = database.practiceStateDao().get(),
                scheduleSlots = database.scheduleSlotDao().getAllOrderedByTime(),
                occurrences = database.questionOccurrenceDao().getAllOrderedByPlannedAt(),
                answers = database.answerDao().getAllOrderedByCreatedAt(),
                deferEvents = database.deferEventDao().getAllOrdered(),
            )
        }
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
