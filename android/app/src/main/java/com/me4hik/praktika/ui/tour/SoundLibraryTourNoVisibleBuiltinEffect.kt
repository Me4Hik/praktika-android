package com.me4hik.praktika.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.R
import com.me4hik.praktika.ui.settings.SoundLibraryItemUi
import com.me4hik.praktika.ui.settings.firstVisibleBuiltinIndex
import com.me4hik.praktika.ui.settings.useRestoreHiddenTourTarget

/**
 * Soft recovery when no selectable sound remains: tip override only.
 * Does **not** auto-advance / User-Skip the Sound task (ActionTour-09).
 */
@Composable
fun SoundLibraryTourNoVisibleBuiltinEffect(
    items: List<SoundLibraryItemUi>,
    hiddenCount: Int,
    isListReady: Boolean,
) {
    val tourController = LocalTourController.current ?: return
    val session by tourController.session.collectAsStateWithLifecycle()
    val firstBuiltin = firstVisibleBuiltinIndex(items)
    val useRestore = useRestoreHiddenTourTarget(items, hiddenCount)
    val stepId = session.currentStep?.id
    val runId = session.runId

    LaunchedEffect(runId, stepId, isListReady, firstBuiltin, hiddenCount, useRestore) {
        if (!tourController.session.value.isActive) return@LaunchedEffect
        val expectedStepId = stepId ?: return@LaunchedEffect
        if (tourController.session.value.currentStep?.id != expectedStepId) return@LaunchedEffect

        when (expectedStepId) {
            TourStepId.PREVIEW_SOUND,
            TourStepId.CHOOSE_SOUND,
            -> {
                if (!isListReady) return@LaunchedEffect
                val noSelectable =
                    if (expectedStepId == TourStepId.CHOOSE_SOUND &&
                        tourController.session.value.currentStep?.targetId ==
                        TourTargetId.SOUND_LIBRARY_ANY_ITEM
                    ) {
                        items.isEmpty()
                    } else {
                        firstBuiltin < 0
                    }
                if (!noSelectable) return@LaunchedEffect
                if (tourController.session.value.currentStep?.id != expectedStepId) {
                    return@LaunchedEffect
                }
                tourController.skipStepImmediately(
                    reason = TourSkipReasons.NO_VISIBLE_BUILTIN,
                    expectedStepId = expectedStepId,
                )
                if (useRestore) {
                    tourController.setTipOverrideResId(R.string.tour_step_hide_restore_all_hidden)
                }
            }
            TourStepId.HIDE_RESTORE_INFO -> {
                if (!isListReady) return@LaunchedEffect
                when {
                    useRestore -> {
                        if (tourController.session.value.currentStep?.id != expectedStepId) {
                            return@LaunchedEffect
                        }
                        tourController.setTipOverrideResId(
                            R.string.tour_step_hide_restore_all_hidden,
                        )
                    }
                    firstBuiltin < 0 -> {
                        if (tourController.session.value.currentStep?.id != expectedStepId) {
                            return@LaunchedEffect
                        }
                        tourController.skipStepImmediately(
                            reason = TourSkipReasons.NO_VISIBLE_BUILTIN,
                            expectedStepId = expectedStepId,
                        )
                    }
                    else -> {
                        if (tourController.session.value.currentStep?.id != expectedStepId) {
                            return@LaunchedEffect
                        }
                        tourController.setTipOverrideResId(null)
                    }
                }
            }
            else -> Unit
        }
    }
}
