package com.me4hik.praktika.ui.settings

import com.me4hik.praktika.sound.SoundAsset

data class SoundLibraryItemUi(
    val asset: SoundAsset,
    val displayName: String,
    val selected: Boolean,
    val canHide: Boolean,
    val previewAvailable: Boolean,
)

data class SoundLibraryUiState(
    val selectedSoundId: String,
    val selectedDisplayName: String,
    val soundEnabled: Boolean,
    val items: List<SoundLibraryItemUi>,
    val hiddenCount: Int,
    val currentlyPreviewingId: String?,
    /** Elapsed preview position; 0 when no active preview. */
    val previewPositionMs: Long = 0L,
    /** Total duration; null when no active preview or duration unknown. */
    val previewDurationMs: Long? = null,
    val messageResId: Int? = null,
)

/** Formats milliseconds as `m:ss` (minutes may exceed 9). */
fun formatPreviewTimeMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

object SoundLibraryTestTags {
    const val SOUND_LIBRARY_ENTRY = "sound_library_entry"
    const val SOUND_LIBRARY_SCREEN = "sound_library_screen"
    const val SOUND_LIBRARY_LIST = "sound_library_list"
    const val SOUND_LIBRARY_SELECTED_LABEL = "sound_library_selected_label"
    const val SOUND_RESTORE_HIDDEN = "sound_restore_hidden"
    const val SOUND_LIBRARY_BACK = "sound_library_back"

    fun item(id: String) = "sound_item_$id"
    fun play(id: String) = "sound_play_$id"
    fun more(id: String) = "sound_more_$id"
    fun previewProgress(id: String) = "sound_preview_progress_$id"
}
