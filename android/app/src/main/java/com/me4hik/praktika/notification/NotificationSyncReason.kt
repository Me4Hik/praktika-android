// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - причины sync coordinator
package com.me4hik.praktika.notification

enum class NotificationSyncReason {
    APP_START,
    FOREGROUND,
    PLANNED_ALARM,
    EXPIRY_ALARM,
    MUTATION,
    SOUND_CHANGED,
    PERMISSION_CHANGED,
    BOOT,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    PACKAGE_REPLACED,
    NOTIFICATION_TAP,
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
