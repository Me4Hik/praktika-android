package com.me4hik.praktika.ui.practice

/**
 * One-shot UI effects from [PracticeRootViewModel].
 *
 * Must be handled by the *current* Activity/composition so Activity-bound
 * [androidx.activity.result.ActivityResultLauncher] / [android.app.Activity.startActivity]
 * are never retained across locale/config recreate.
 */
sealed class PracticeRootUiEvent {
    data object RequestPostNotifications : PracticeRootUiEvent()

    data object OpenAppNotificationSettings : PracticeRootUiEvent()

    data object OpenChannelSettings : PracticeRootUiEvent()

    data object OpenExactAlarmSettings : PracticeRootUiEvent()
}
