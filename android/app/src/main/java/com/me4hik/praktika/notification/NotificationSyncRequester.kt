// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - port sync после mutation
package com.me4hik.praktika.notification

fun interface NotificationSyncRequester {
    suspend fun requestSync(reason: NotificationSyncReason)
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
