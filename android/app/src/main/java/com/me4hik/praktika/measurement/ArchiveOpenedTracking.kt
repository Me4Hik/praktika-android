package com.me4hik.praktika.measurement

import androidx.lifecycle.SavedStateHandle

/**
 * Once-per-Archive-hub NavBackStackEntry guard for archive_opened.
 *
 * Flag lives on the entry SavedStateHandle, so it survives configuration change /
 * process restore of the same logical Archive hub entry, but resets when that entry
 * is popped and Archive is opened again.
 */
object ArchiveOpenedTracking {
    const val SAVED_STATE_KEY = "archive_opened_tracked"

    fun trackOnce(
        savedStateHandle: SavedStateHandle,
        track: () -> Unit,
    ) {
        if (savedStateHandle.get<Boolean>(SAVED_STATE_KEY) == true) {
            return
        }
        track()
        savedStateHandle[SAVED_STATE_KEY] = true
    }
}
