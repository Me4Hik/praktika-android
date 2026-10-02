package com.me4hik.praktika.ui.tour

import androidx.compose.runtime.staticCompositionLocalOf

val LocalTourController = staticCompositionLocalOf<TourController?> { null }

val LocalTourTargetRegistry = staticCompositionLocalOf<TourTargetRegistry?> { null }

/** Dp reserved under window top so targets can reveal below fixed-top chrome. */
val LocalTourChromeTopInsetDp = staticCompositionLocalOf {
    TourChromePlacement.TOP_TARGET_INSET_DP
}

fun TourController?.notifyActivation(targetId: TourTargetId) {
    this?.onTargetActivated(targetId)
}
