package com.me4hik.praktika.ui.tour

enum class TourStepType {
    TARGET_CLICK,
    USER_CHOICE,
    SHOW_ONLY,
    NAV_BACK,
}

enum class TourNavAction {
    NONE,
    POP_TO_HOME,
    POP_ONCE,
    NAVIGATE_ARCHIVE,
    NAVIGATE_ARCHIVE_DAYS,
    NAVIGATE_SETTINGS,
    NAVIGATE_NOTIFICATIONS,
    NAVIGATE_SOUND_LIBRARY,
}

sealed interface TourCompletion {
    /** SHOW_ONLY — user taps Далее. */
    data object ManualAdvance : TourCompletion

    /** TARGET_CLICK / USER_CHOICE — target reports activation. */
    data object TargetActivation : TourCompletion

    /** Route prefix match (e.g. after navigation click). */
    data class RouteMatch(val prefix: String) : TourCompletion

    /** NAV_BACK — current route left expected prefix. */
    data class LeftRoute(val prefix: String) : TourCompletion
}

data class TourStep(
    val id: TourStepId,
    val type: TourStepType,
    val tipResId: Int,
    /** Short label for exit summary; not taken from enum ordinal. */
    val shortTitleResId: Int = tipResId,
    val targetId: TourTargetId? = null,
    val expectedRoutePrefix: String? = null,
    val onEnterNav: TourNavAction = TourNavAction.NONE,
    val onSkipNav: TourNavAction = TourNavAction.NONE,
    val completion: TourCompletion,
    val allowScroll: Boolean = false,
    val bringIntoView: Boolean = false,
)

data class TourDefinition(
    val id: String,
    val steps: List<TourStep>,
)

enum class TourStatus {
    Idle,
    Active,
    Completed,
    Exited,
}

data class TourSessionState(
    val status: TourStatus = TourStatus.Idle,
    val definitionId: String? = null,
    val runId: String? = null,
    val stepIndex: Int = 0,
    val steps: List<TourStep> = emptyList(),
    val completedStepIds: List<TourStepId> = emptyList(),
    val skippedStepIds: List<TourStepId> = emptyList(),
    val skipReasons: Map<TourStepId, String> = emptyMap(),
    val exitStepId: TourStepId? = null,
    val startedAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
    val waitingForTarget: Boolean = false,
    /** Optional tip override for the current step (e.g. sound all-hidden restore copy). */
    val tipOverrideResId: Int? = null,
) {
    val isActive: Boolean get() = status == TourStatus.Active
    val currentStep: TourStep? get() = steps.getOrNull(stepIndex)
    val stepOrdinal: Int get() = if (steps.isEmpty()) 0 else stepIndex + 1
    val stepCount: Int get() = steps.size
    val completedTour: Boolean get() = status == TourStatus.Completed
    val effectiveTipResId: Int?
        get() = tipOverrideResId ?: currentStep?.tipResId
}

object TourSkipReasons {
    const val NO_VISIBLE_BUILTIN = "no_visible_builtin"
    const val TARGET_UNAVAILABLE = "target_unavailable"
}

data class TourRunResult(
    val runId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val completedStepIds: List<String>,
    val skippedStepIds: List<String>,
    val exitStepId: String?,
    val completedTour: Boolean,
)

/** Schedule slots stay editable only on the interactive schedule tour step. */
fun isTourScheduleInteractive(tourActive: Boolean, step: TourStep?): Boolean {
    if (!tourActive) return true
    return step?.id == TourStepId.SCHEDULE_INFO &&
        step.targetId == TourTargetId.SETTINGS_SCHEDULE
}
