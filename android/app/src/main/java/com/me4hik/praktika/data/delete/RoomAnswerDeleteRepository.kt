// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - Room delete Answer по answerId
package com.me4hik.praktika.data.delete

import com.me4hik.praktika.data.backup.write.BackupMutationRequestSink
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.local.PraktikaDatabase
import kotlin.coroutines.cancellation.CancellationException

class RoomAnswerDeleteRepository(
    private val database: PraktikaDatabase,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation sink
    private val backupMutationRequestSink: BackupMutationRequestSink,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
) : AnswerDeleteRepository {

    override suspend fun deleteAnswer(answerId: Long) {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C delete changed-row trigger
        val rowsDeleted = database.answerDao().deleteById(answerId)
        if (rowsDeleted > 0) {
            requestMutationBackup(BackupRequestReason.ANSWER_DELETED)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation sink boundary
    private fun requestMutationBackup(reason: BackupRequestReason) {
        try {
            backupMutationRequestSink.requestBackup(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ordinary backup enqueue failure must not undo committed delete.
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
