package com.me4hik.praktika.actiontoursmoke

/**
 * Pure contracts for Task4 Sound Library black-box proof (no top-bar title).
 */
object SoundSmokeProof {
    const val SELECTED_CONTENT_DESCRIPTION = "Выбрано"
    const val TIP_CONTAINS = "Выберите любой звук уведомления"

    /**
     * Screen accepted without requiring title "Звук уведомления"
     * (title is often outside tour spotlight hole).
     */
    fun soundLibraryScreenAccepted(
        hasProgressTask4: Boolean,
        tipLooksLikeSoundTask: Boolean,
        hasSelectedCdOrChecked: Boolean,
    ): Boolean = hasProgressTask4 && tipLooksLikeSoundTask && hasSelectedCdOrChecked

    /** Never treat an unselected row as a safe same-value target. */
    fun allowUnselectedSoundFallback(): Boolean = false
}
