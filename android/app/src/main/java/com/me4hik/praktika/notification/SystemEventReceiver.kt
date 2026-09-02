// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - system event receiver
// 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik START - durable SYSTEM_EVENT_RECEIVED
package com.me4hik.praktika.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val action = intent.action
                val reason = SystemEventSyncReasonResolver.resolve(action) ?: return@launch
                TargetedBugDiagnostics.recordSystemEventReceived(
                    action = action.orEmpty(),
                    syncReason = reason.name,
                    wallClockMs = System.currentTimeMillis(),
                    elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                )
                val runtime = PraktikaRuntimeHolder.get(context)
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
// 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
