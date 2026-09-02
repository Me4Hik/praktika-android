// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - system event sync reason mapping
package com.me4hik.praktika.notification

import android.content.Intent

/**
 * Maps system broadcast actions handled by [SystemEventReceiver] to [NotificationSyncReason].
 * Kept separate so host tests can assert mappings without invoking BroadcastReceiver delivery.
 */
internal object SystemEventSyncReasonResolver {
    fun resolve(action: String?): NotificationSyncReason? {
        return when (action) {
            Intent.ACTION_BOOT_COMPLETED -> NotificationSyncReason.BOOT
            Intent.ACTION_TIME_CHANGED,
            "android.intent.action.TIME_SET",
            -> NotificationSyncReason.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> NotificationSyncReason.TIMEZONE_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED -> NotificationSyncReason.PACKAGE_REPLACED
            else -> null
        }
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
