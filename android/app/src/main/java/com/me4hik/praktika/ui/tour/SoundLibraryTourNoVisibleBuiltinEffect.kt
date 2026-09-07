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
 * Sound-library tour hook: when the list is ready and no visible builtin remains,
 * immediately skip preview/select and retarget hide-info to Restore (without mutating prefs).
 *
 * Unready placeholder (`isListReady == false`) must never be treated as all-hidden.
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
        // Re-check: a prior skip in this composition may already have advanced the step.
        if (tourController.session.value.currentStep?.id != expectedStepId) return@LaunchedEffect

        when (expectedStepId) {
            TourStepId.PREVIEW_SOUND,
            TourStepId.CHOOSE_SOUND,
            -> {
                if (!isListReady) return@LaunchedEffect
                if (firstBuiltin >= 0) return@LaunchedEffect
                if (tourController.session.value.currentStep?.id != expectedStepId) {
                    return@LaunchedEffect
                }
                tourController.skipStepImmediately(
                    reason = TourSkipReasons.NO_VISIBLE_BUILTIN,
                    expectedStepId = expectedStepId,
                )
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
                        // Ready anomaly: no builtin and no restore entry — fail-soft once.
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
