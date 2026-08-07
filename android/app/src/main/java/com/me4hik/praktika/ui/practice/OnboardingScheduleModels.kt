// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - UI models расписания onboarding
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.ui.settings.formatTimeOfDayMinutes

data class OnboardingScheduleUiModel(
    val slotIndex: Int,
    val timeOfDayMinutes: Int,
    val timeText: String,
)

enum class OnboardingScheduleError {
    DUPLICATE_TIME,
}

object OnboardingSavedStateKeys {
    const val DRAFT_SLOT_1 = "onboarding_draft_slot_1"
    const val DRAFT_SLOT_2 = "onboarding_draft_slot_2"
    const val DRAFT_SLOT_3 = "onboarding_draft_slot_3"
    const val DRAFT_INITIALIZED = "onboarding_draft_initialized"
}

internal fun onboardingSlotTestTag(slotIndex: Int): String {
    return when (slotIndex) {
        1 -> PracticeTestTags.ONBOARDING_SLOT_1
        2 -> PracticeTestTags.ONBOARDING_SLOT_2
        3 -> PracticeTestTags.ONBOARDING_SLOT_3
        else -> PracticeTestTags.ONBOARDING_SLOT_1
    }
}

internal fun buildOnboardingSlotUiModels(minutesByIndex: Map<Int, Int>): List<OnboardingScheduleUiModel> {
    return listOf(1, 2, 3).map { slotIndex ->
        val minutes = minutesByIndex.getValue(slotIndex)
        OnboardingScheduleUiModel(
            slotIndex = slotIndex,
            timeOfDayMinutes = minutes,
            timeText = formatTimeOfDayMinutes(minutes),
        )
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
