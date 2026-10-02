package com.me4hik.praktika.ui.tour

import com.me4hik.praktika.navigation.Routes

enum class TourStepType {
    TARGET_CLICK,
    USER_CHOICE,
    SHOW_ONLY,
    NAV_BACK,
    GATE,
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

enum class TourUiPhase {
    INTRO,
    ACTION,
    GATE,
    OVERVIEW,
    /** Post-overview farewell — not counted in Обзор N из M. */
    COMPLETION,
}

/** Terminal outcomes for ACTION CORE tasks. Technical failures are not terminal. */
enum class TourTaskOutcome {
    Pending,
    Completed,
    UserSkipped,
}

sealed interface TourCompletion {
    data object ManualAdvance : TourCompletion
    data object TargetActivation : TourCompletion
    data class RouteMatch(val prefix: String) : TourCompletion
    data class LeftRoute(val prefix: String) : TourCompletion
    data object GateChoice : TourCompletion
}

data class TourStep(
    val id: TourStepId,
    val type: TourStepType,
    val tipResId: Int,
    val shortTitleResId: Int = tipResId,
    val targetId: TourTargetId? = null,
    val taskId: TourTaskId? = null,
    val expectedRoutePrefix: String? = null,
    val onEnterNav: TourNavAction = TourNavAction.NONE,
    val onEnterNavActions: List<TourNavAction> = emptyList(),
    val onSkipNav: TourNavAction = TourNavAction.NONE,
    val onCompleteNav: TourNavAction = TourNavAction.NONE,
    val completion: TourCompletion,
    val allowScroll: Boolean = false,
    val bringIntoView: Boolean = false,
) {
    fun resolvedEnterNavActions(): List<TourNavAction> {
        if (onEnterNavActions.isNotEmpty()) return onEnterNavActions
        return if (onEnterNav != TourNavAction.NONE) listOf(onEnterNav) else emptyList()
    }

    fun requiresActionCue(): Boolean =
        type == TourStepType.TARGET_CLICK || type == TourStepType.USER_CHOICE

    /**
     * Chrome Skip visibility without per-step flags or controller branches:
     * Intro keeps Skip; ACTION only when the step is still actionable.
     * Confirmation SHOW_ONLY inside a task (e.g. Task1 1C) → Exit + Далее only.
     */
    fun showsChromeSkip(): Boolean = when (uiPhase()) {
        TourUiPhase.INTRO -> true
        TourUiPhase.ACTION -> requiresActionCue()
        TourUiPhase.GATE, TourUiPhase.OVERVIEW, TourUiPhase.COMPLETION -> false
    }

    /**
     * Chrome CTA pulse for ManualAdvance steps where Next/Done is the only forward action.
     * Intro intentionally excluded (product). Gate never uses Next/Done.
     */
    fun showsManualAdvanceCue(): Boolean {
        if (completion != TourCompletion.ManualAdvance) return false
        if (id == TourStepId.START_INTRO) return false
        return when (uiPhase()) {
            TourUiPhase.ACTION, TourUiPhase.OVERVIEW, TourUiPhase.COMPLETION -> true
            TourUiPhase.INTRO, TourUiPhase.GATE -> false
        }
    }

    fun uiPhase(): TourUiPhase = when {
        type == TourStepType.GATE || id == TourStepId.PHASE_GATE -> TourUiPhase.GATE
        id == TourStepId.TOUR_COMPLETION -> TourUiPhase.COMPLETION
        taskId != null -> TourUiPhase.ACTION
        id == TourStepId.START_INTRO -> TourUiPhase.INTRO
        else -> TourUiPhase.OVERVIEW
    }
}

data class TourDefinition(
    val id: String,
    val steps: List<TourStep>,
) {
    val actionTaskOrder: List<TourTaskId> =
        steps.mapNotNull { it.taskId }.distinct()

    val actionTaskCount: Int get() = actionTaskOrder.size

    val overviewSteps: List<TourStep> =
        steps.filter { it.uiPhase() == TourUiPhase.OVERVIEW }
}

enum class TourStatus {
    Idle,
    Active,
    Completed,
    Exited,
}

sealed interface TourProgressDisplay {
    data object Hidden : TourProgressDisplay
    data class Action(val ordinal: Int, val count: Int) : TourProgressDisplay
    data object Gate : TourProgressDisplay
    data class Overview(val ordinal: Int, val count: Int) : TourProgressDisplay
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
    val tipOverrideResId: Int? = null,
    val coreCompleted: Boolean = false,
    /**
     * Action TARGET_CLICK / USER_CHOICE armed only after canonical route is confirmed.
     * Intro / Gate / Overview without route gate stay ready.
     */
    val actionReady: Boolean = false,
    /** Increments on every step transition; stamps nav emissions. */
    val navGeneration: Long = 0L,
    val taskOutcomes: Map<TourTaskId, TourTaskOutcome> = emptyMap(),
) {
    val isActive: Boolean get() = status == TourStatus.Active
    val currentStep: TourStep? get() = steps.getOrNull(stepIndex)
    val definition: TourDefinition
        get() = TourDefinition(id = definitionId.orEmpty(), steps = steps)

    val stepOrdinal: Int get() = if (steps.isEmpty()) 0 else stepIndex + 1
    val stepCount: Int get() = steps.size
    val completedTour: Boolean get() = status == TourStatus.Completed
    val effectiveTipResId: Int?
        get() = tipOverrideResId ?: currentStep?.tipResId

    val uiPhase: TourUiPhase
        get() = currentStep?.uiPhase() ?: TourUiPhase.INTRO

    val progressDisplay: TourProgressDisplay
        get() {
            val step = currentStep ?: return TourProgressDisplay.Hidden
            return when (step.uiPhase()) {
                TourUiPhase.INTRO,
                TourUiPhase.COMPLETION,
                -> TourProgressDisplay.Hidden
                TourUiPhase.GATE -> TourProgressDisplay.Gate
                TourUiPhase.ACTION -> {
                    val task = step.taskId ?: return TourProgressDisplay.Hidden
                    val order = definition.actionTaskOrder
                    val ordinal = order.indexOf(task) + 1
                    TourProgressDisplay.Action(ordinal = ordinal.coerceAtLeast(1), count = order.size)
                }
                TourUiPhase.OVERVIEW -> {
                    val overview = definition.overviewSteps
                    val ordinal = overview.indexOfFirst { it.id == step.id } + 1
                    TourProgressDisplay.Overview(
                        ordinal = ordinal.coerceAtLeast(1),
                        count = overview.size,
                    )
                }
            }
        }

    val showActionCue: Boolean
        get() = isActive &&
            actionReady &&
            (currentStep?.requiresActionCue() == true)

    fun allActionTasksTerminal(): Boolean {
        val order = definition.actionTaskOrder
        if (order.isEmpty()) return true
        return order.all { task ->
            when (taskOutcomes[task] ?: TourTaskOutcome.Pending) {
                TourTaskOutcome.Completed, TourTaskOutcome.UserSkipped -> true
                TourTaskOutcome.Pending -> false
            }
        }
    }
}

object TourSkipReasons {
    const val NO_VISIBLE_BUILTIN = "no_visible_builtin"
    const val TARGET_UNAVAILABLE = "target_unavailable"
    const val USER_SKIP = "user_skip"
    const val ROUTE_RECOVERY = "route_recovery"
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

data class TourNavCommand(
    val action: TourNavAction,
    val generation: Long,
)

/**
 * Pure navigation planner — keeps Notifications parent `settings` on the back stack.
 */
object TourNavPlanner {
    fun routeMatchesExpected(route: String?, expected: String?): Boolean {
        if (expected == null) return true
        return route == expected
    }

    fun planNavigateToNotifications(currentRoute: String?): List<TourNavAction> {
        return when {
            currentRoute == Routes.SETTINGS_NOTIFICATIONS -> emptyList()
            currentRoute == Routes.SOUND_LIBRARY -> listOf(TourNavAction.POP_ONCE)
            currentRoute == Routes.SETTINGS -> listOf(TourNavAction.NAVIGATE_NOTIFICATIONS)
            else -> listOf(
                TourNavAction.NAVIGATE_SETTINGS,
                TourNavAction.NAVIGATE_NOTIFICATIONS,
            )
        }
    }

    fun expandNavAction(action: TourNavAction, currentRoute: String?): List<TourNavAction> {
        return when (action) {
            TourNavAction.NONE -> emptyList()
            TourNavAction.NAVIGATE_NOTIFICATIONS -> planNavigateToNotifications(currentRoute)
            else -> listOf(action)
        }
    }
}

fun isTourScheduleInteractive(tourActive: Boolean, step: TourStep?): Boolean {
    if (!tourActive) return true
    return step?.id == TourStepId.SCHEDULE_INFO &&
        step.targetId == TourTargetId.SETTINGS_SCHEDULE
}
