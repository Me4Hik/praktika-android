package com.me4hik.praktika.ui.tour

import com.me4hik.praktika.R
import com.me4hik.praktika.navigation.Routes

/**
 * Experimental CORE training tour — action-first (5 tasks) + optional overview.
 * Definition id kept for compatibility with existing tester tooling.
 */
object ExperimentalCoreTourV1 {
    const val DEFINITION_ID = "experimental_core_v1"

    val definition: TourDefinition = TourDefinition(
        id = DEFINITION_ID,
        steps = listOf(
            TourStep(
                id = TourStepId.START_INTRO,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_step_start_intro,
                shortTitleResId = R.string.tour_title_start_intro,
                completion = TourCompletion.ManualAdvance,
            ),

            // Task 1 — Archive by days (1A → 1B → 1C confirmation)
            TourStep(
                id = TourStepId.CLICK_ARCHIVE,
                type = TourStepType.TARGET_CLICK,
                tipResId = R.string.tour_task_archive,
                shortTitleResId = R.string.tour_title_task_archive_days,
                targetId = TourTargetId.HOME_ARCHIVE,
                taskId = TourTaskId.ARCHIVE_DAYS,
                expectedRoutePrefix = Routes.HOME,
                onEnterNav = TourNavAction.POP_TO_HOME,
                completion = TourCompletion.RouteMatch(Routes.ARCHIVE),
                onSkipNav = TourNavAction.POP_TO_HOME,
            ),
            TourStep(
                id = TourStepId.CLICK_ARCHIVE_DAYS,
                type = TourStepType.TARGET_CLICK,
                tipResId = R.string.tour_task_archive_days,
                shortTitleResId = R.string.tour_title_task_archive_days,
                targetId = TourTargetId.ARCHIVE_OPEN_DAYS,
                taskId = TourTaskId.ARCHIVE_DAYS,
                expectedRoutePrefix = Routes.ARCHIVE,
                completion = TourCompletion.RouteMatch(Routes.ARCHIVE_DAYS),
                onSkipNav = TourNavAction.POP_TO_HOME,
            ),
            TourStep(
                id = TourStepId.ARCHIVE_DAYS_CONTENT,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_task_archive_days_info,
                shortTitleResId = R.string.tour_title_task_archive_days,
                targetId = TourTargetId.ARCHIVE_DAYS_CONTENT,
                taskId = TourTaskId.ARCHIVE_DAYS,
                expectedRoutePrefix = Routes.ARCHIVE_DAYS,
                completion = TourCompletion.ManualAdvance,
                onSkipNav = TourNavAction.POP_TO_HOME,
                onCompleteNav = TourNavAction.POP_TO_HOME,
            ),

            // Task 2 — Schedule
            TourStep(
                id = TourStepId.CLICK_SETTINGS,
                type = TourStepType.TARGET_CLICK,
                tipResId = R.string.tour_task_schedule,
                shortTitleResId = R.string.tour_title_task_schedule,
                targetId = TourTargetId.HOME_SETTINGS,
                taskId = TourTaskId.SCHEDULE,
                expectedRoutePrefix = Routes.HOME,
                completion = TourCompletion.RouteMatch(Routes.SETTINGS),
                onSkipNav = TourNavAction.NAVIGATE_SETTINGS,
            ),
            TourStep(
                id = TourStepId.SCHEDULE_INFO,
                type = TourStepType.USER_CHOICE,
                tipResId = R.string.tour_task_schedule_change,
                shortTitleResId = R.string.tour_title_task_schedule,
                targetId = TourTargetId.SETTINGS_SCHEDULE,
                taskId = TourTaskId.SCHEDULE,
                expectedRoutePrefix = Routes.SETTINGS,
                completion = TourCompletion.TargetActivation,
                allowScroll = true,
                bringIntoView = true,
                onSkipNav = TourNavAction.NONE,
            ),

            // Task 3 — Wording
            TourStep(
                id = TourStepId.CHOOSE_WORDING,
                type = TourStepType.USER_CHOICE,
                tipResId = R.string.tour_task_wording,
                shortTitleResId = R.string.tour_title_task_wording,
                targetId = TourTargetId.SETTINGS_WORDING,
                taskId = TourTaskId.WORDING,
                expectedRoutePrefix = Routes.SETTINGS,
                completion = TourCompletion.TargetActivation,
                allowScroll = true,
                bringIntoView = true,
            ),

            // Task 4 — Sound
            TourStep(
                id = TourStepId.CLICK_NOTIFICATIONS,
                type = TourStepType.TARGET_CLICK,
                tipResId = R.string.tour_task_sound_notifications,
                shortTitleResId = R.string.tour_title_task_sound,
                targetId = TourTargetId.SETTINGS_NOTIFICATIONS,
                taskId = TourTaskId.SOUND,
                expectedRoutePrefix = Routes.SETTINGS,
                completion = TourCompletion.RouteMatch(Routes.SETTINGS_NOTIFICATIONS),
                allowScroll = true,
                bringIntoView = true,
                onSkipNav = TourNavAction.NAVIGATE_NOTIFICATIONS,
            ),
            TourStep(
                id = TourStepId.CLICK_SOUND_LIBRARY,
                type = TourStepType.TARGET_CLICK,
                tipResId = R.string.tour_task_sound_library,
                shortTitleResId = R.string.tour_title_task_sound,
                targetId = TourTargetId.NOTIFICATIONS_SOUND_LIBRARY,
                taskId = TourTaskId.SOUND,
                expectedRoutePrefix = Routes.SETTINGS_NOTIFICATIONS,
                completion = TourCompletion.RouteMatch(Routes.SOUND_LIBRARY),
                onSkipNav = TourNavAction.NONE,
            ),
            TourStep(
                id = TourStepId.CHOOSE_SOUND,
                type = TourStepType.USER_CHOICE,
                tipResId = R.string.tour_task_sound,
                shortTitleResId = R.string.tour_title_task_sound,
                targetId = TourTargetId.SOUND_LIBRARY_ANY_ITEM,
                taskId = TourTaskId.SOUND,
                expectedRoutePrefix = Routes.SOUND_LIBRARY,
                completion = TourCompletion.TargetActivation,
                onSkipNav = TourNavAction.POP_ONCE,
                onCompleteNav = TourNavAction.POP_ONCE,
            ),

            // Task 5 — Defer
            TourStep(
                id = TourStepId.CHOOSE_DEFER,
                type = TourStepType.USER_CHOICE,
                tipResId = R.string.tour_task_defer,
                shortTitleResId = R.string.tour_title_task_defer,
                targetId = TourTargetId.NOTIFICATIONS_DEFER,
                taskId = TourTaskId.DEFER,
                expectedRoutePrefix = Routes.SETTINGS_NOTIFICATIONS,
                completion = TourCompletion.TargetActivation,
                allowScroll = true,
                bringIntoView = true,
            ),

            // Gate (CORE already persisted completed)
            TourStep(
                id = TourStepId.PHASE_GATE,
                type = TourStepType.GATE,
                tipResId = R.string.tour_step_phase_gate,
                shortTitleResId = R.string.tour_title_phase_gate,
                expectedRoutePrefix = Routes.SETTINGS_NOTIFICATIONS,
                completion = TourCompletion.GateChoice,
            ),

            // Phase B — optional overview
            TourStep(
                id = TourStepId.OVERVIEW_HOME,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_overview_home,
                shortTitleResId = R.string.tour_title_overview_home,
                targetId = TourTargetId.HOME_OVERVIEW,
                expectedRoutePrefix = Routes.HOME,
                onEnterNav = TourNavAction.POP_TO_HOME,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.OVERVIEW_ARCHIVE,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_overview_archive,
                shortTitleResId = R.string.tour_title_overview_archive,
                targetId = TourTargetId.ARCHIVE_HUB,
                expectedRoutePrefix = Routes.ARCHIVE,
                onEnterNav = TourNavAction.NAVIGATE_ARCHIVE,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.OVERVIEW_EXPORT_SHARE,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_overview_export_share,
                shortTitleResId = R.string.tour_title_overview_export_share,
                targetId = TourTargetId.ARCHIVE_EXPORT_SECTION,
                expectedRoutePrefix = Routes.ARCHIVE,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.OVERVIEW_PAUSE_BACKUP,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_overview_pause_backup,
                shortTitleResId = R.string.tour_title_overview_pause_backup,
                targetId = TourTargetId.SETTINGS_PAUSE_BACKUP,
                expectedRoutePrefix = Routes.SETTINGS,
                onEnterNavActions = listOf(
                    TourNavAction.POP_TO_HOME,
                    TourNavAction.NAVIGATE_SETTINGS,
                ),
                completion = TourCompletion.ManualAdvance,
                allowScroll = true,
                bringIntoView = true,
            ),
            // Final farewell — not part of Обзор N из M
            TourStep(
                id = TourStepId.TOUR_COMPLETION,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_completion_farewell,
                shortTitleResId = R.string.tour_title_completion,
                completion = TourCompletion.ManualAdvance,
            ),
        ),
    )
}

/** Compatibility alias for older call sites / tests. */
object ExperimentalVerticalSliceTour {
    const val DEFINITION_ID = ExperimentalCoreTourV1.DEFINITION_ID
    val definition: TourDefinition get() = ExperimentalCoreTourV1.definition
}
