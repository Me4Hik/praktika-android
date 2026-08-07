// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - write API удаления Answer
package com.me4hik.praktika.data.delete

interface AnswerDeleteRepository {
    suspend fun deleteAnswer(answerId: Long)
}
// 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
