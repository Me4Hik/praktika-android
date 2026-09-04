// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - причины sync coordinator
package com.me4hik.praktika.notification

enum class NotificationSyncReason {
    APP_START,
    FOREGROUND,
    PLANNED_ALARM,
    EXPIRY_ALARM,
    DEFERRED_ALARM,
    MUTATION,
    SOUND_CHANGED,
    PERMISSION_CHANGED,
    BOOT,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    PACKAGE_REPLACED,
    EXACT_ALARM_PERMISSION_CHANGED,
    NOTIFICATION_TAP,
    ;

    /**
     * Catch-up sync that may re-materialize a due QUESTION after dismiss/process death.
     * Must not re-fire audible/heads-up alert; alarm-driven reasons stay alerting.
     */
    fun isQuietCatchUp(): Boolean {
        return this == APP_START || this == FOREGROUND
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
