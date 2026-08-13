// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact scheduled reminder delivery
package com.me4hik.praktika.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExactAlarmPermissionStateReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        if (intent?.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                if (!PraktikaRuntimeHolder.isInitialized()) {
                    return@launch
                }
                val runtime = PraktikaRuntimeHolder.get(context.applicationContext)
                if (runtime.exactAlarmCapabilityRepository.notifyCapabilityChanged("broadcast")) {
                    runtime.notificationCoordinator.sync(NotificationSyncReason.EXACT_ALARM_PERMISSION_CHANGED)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
