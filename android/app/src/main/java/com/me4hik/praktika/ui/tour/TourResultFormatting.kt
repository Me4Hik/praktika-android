package com.me4hik.praktika.ui.tour

import android.content.Context
import com.me4hik.praktika.R

fun formatTourResultSummary(
    context: Context,
    result: TourRunResult,
    definition: TourDefinition = ExperimentalCoreTourV1.definition,
): String {
    val base = if (result.completedTour) {
        context.getString(R.string.settings_tour_result_completed)
    } else {
        val stepNumber = exitStepNumberInDefinition(result.exitStepId, definition)
        val stepTitle = exitStepTitleInDefinition(context, result.exitStepId, definition)
        context.getString(R.string.settings_tour_result_exited, stepNumber, stepTitle)
    }
    return if (result.skippedStepIds.isNotEmpty()) {
        base + " " + context.getString(
            R.string.settings_tour_result_skipped,
            result.skippedStepIds.size,
        )
    } else {
        base
    }
}

/** 1-based index in [definition], or 0 if unknown. */
fun exitStepNumberInDefinition(
    exitStepId: String?,
    definition: TourDefinition,
): Int {
    if (exitStepId == null) return 0
    val index = definition.steps.indexOfFirst { it.id.name == exitStepId }
    return if (index >= 0) index + 1 else 0
}

fun exitStepTitleInDefinition(
    context: Context,
    exitStepId: String?,
    definition: TourDefinition,
): String {
    if (exitStepId == null) return ""
    val step = definition.steps.firstOrNull { it.id.name == exitStepId } ?: return ""
    return context.getString(step.shortTitleResId)
}
