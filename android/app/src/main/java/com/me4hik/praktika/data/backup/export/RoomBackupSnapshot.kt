// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - immutable Room snapshot entities
package com.me4hik.praktika.data.backup.export

import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity

internal data class RoomBackupSnapshot(
    val practiceState: PracticeStateEntity?,
    val scheduleSlots: List<ScheduleSlotEntity>,
    val occurrences: List<QuestionOccurrenceEntity>,
    val answers: List<AnswerEntity>,
)
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
