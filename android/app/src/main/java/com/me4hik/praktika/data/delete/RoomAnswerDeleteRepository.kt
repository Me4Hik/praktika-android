// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - Room delete Answer по answerId
package com.me4hik.praktika.data.delete

import com.me4hik.praktika.data.local.PraktikaDatabase

class RoomAnswerDeleteRepository(
    private val database: PraktikaDatabase,
) : AnswerDeleteRepository {

    override suspend fun deleteAnswer(answerId: Long) {
        database.answerDao().deleteById(answerId)
    }
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
