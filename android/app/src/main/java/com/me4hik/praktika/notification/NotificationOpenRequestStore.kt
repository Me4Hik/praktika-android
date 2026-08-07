// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - pending navigation из notification
package com.me4hik.praktika.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationOpenRequest(
    val occurrenceId: Long,
)

class NotificationOpenRequestStore {
    private val _pendingOpen = MutableStateFlow<NotificationOpenRequest?>(null)
    val pendingOpen: StateFlow<NotificationOpenRequest?> = _pendingOpen.asStateFlow()

    fun publish(request: NotificationOpenRequest) {
        _pendingOpen.value = request
    }

    fun consume(): NotificationOpenRequest? {
        val current = _pendingOpen.value ?: return null
        _pendingOpen.value = null
        return current
    }

    internal fun resetForTests() {
        _pendingOpen.value = null
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
