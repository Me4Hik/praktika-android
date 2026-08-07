// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - system event receiver
package com.me4hik.praktika.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val runtime = PraktikaRuntimeHolder.get(context)
                val reason = when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> NotificationSyncReason.BOOT
                    Intent.ACTION_TIME_CHANGED,
                    "android.intent.action.TIME_SET",
                    -> NotificationSyncReason.TIME_CHANGED
                    Intent.ACTION_TIMEZONE_CHANGED -> NotificationSyncReason.TIMEZONE_CHANGED
                    Intent.ACTION_MY_PACKAGE_REPLACED -> NotificationSyncReason.PACKAGE_REPLACED
                    else -> return@launch
                }
                runtime.initializer.ensureInitialized()
                runtime.notificationCoordinator.sync(reason)
            } catch (exception: Exception) {
                Log.e(TAG, "System receiver failed action=${intent.action}", exception)
            } finally {
                // 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik START - goAsync null in direct test invoke
                pendingResult?.finish()
                // 06.08.2026 Stage 12 Final Acceptance cursor by Me4Hik END
            }
        }
    }

    companion object {
        private const val TAG = "SystemEventReceiver"
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
