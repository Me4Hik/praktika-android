// 06.08.2026 Settings Schedule cursor by Me4Hik START - команды SettingsViewModel
package com.me4hik.praktika.ui.settings

import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult

fun interface UpdateScheduleCommand {
    suspend fun updateSchedule(updates: List<ScheduleSlotUpdate>): ScheduleUpdateResult
}

fun interface PausePracticeCommand {
    suspend fun pausePractice(): CycleResult
}

fun interface ResumePracticeCommand {
    suspend fun resumePractice(): CycleResult
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
