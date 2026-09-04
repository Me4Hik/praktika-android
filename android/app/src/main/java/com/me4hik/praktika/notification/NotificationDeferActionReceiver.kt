// QUESTION_DEFER_FINAL_V1 — notification action «Отложить» (no Activity)
package com.me4hik.praktika.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationDeferActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, INVALID_ID)
        val plannedAt = intent.getLongExtra(EXTRA_PLANNED_AT, INVALID_ID)
        if (occurrenceId <= 0L || plannedAt <= 0L) {
            Log.w(TAG, "Ignore defer action with invalid extras occurrenceId=$occurrenceId plannedAt=$plannedAt")
            return
        }
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val runtime = PraktikaRuntimeHolder.get(appContext)
                val durationMinutes = DeferDurationOptions.sanitize(
                    runtime.deferDurationPreferenceRepository.deferDurationMinutes.first(),
                )
                runtime.notificationCoordinator.handleNotificationDefer(
                    occurrenceId = occurrenceId,
                    plannedAtEpochMillis = plannedAt,
                    durationMinutes = durationMinutes,
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Notification defer action failed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val TAG = "NotificationDeferAction"
        const val EXTRA_OCCURRENCE_ID = "notification_occurrence_id"
        const val EXTRA_PLANNED_AT = "notification_planned_at"
        private const val INVALID_ID = -1L
    }
}
