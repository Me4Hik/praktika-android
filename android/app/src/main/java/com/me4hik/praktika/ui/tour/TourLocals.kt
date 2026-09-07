package com.me4hik.praktika.ui.tour

import androidx.compose.runtime.staticCompositionLocalOf

val LocalTourController = staticCompositionLocalOf<TourController?> { null }

val LocalTourTargetRegistry = staticCompositionLocalOf<TourTargetRegistry?> { null }

fun TourController?.notifyActivation(targetId: TourTargetId) {
    this?.onTargetActivated(targetId)
}
