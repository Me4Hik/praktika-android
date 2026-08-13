// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - alarm receiver
package com.me4hik.praktika.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PracticeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, INVALID_ID)
        val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)
        val receivedAtEpochMs = System.currentTimeMillis()
        TargetedBugDiagnostics.recordAlarmFired(
            occurrenceId = occurrenceId,
            alarmType = eventType?.lowercase()?.substringBefore("_boundary"),
            receivedAtEpochMs = receivedAtEpochMs,
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val boundaryEpoch = intent.getLongExtra(EXTRA_BOUNDARY_EPOCH_MILLIS, INVALID_ID)
                val receivedSystemWallTime = receivedAtEpochMs
                // 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik START - receiver delivery diagnostic log
                Log.i(
                    TAG,
                    "onReceive occurrenceId=$occurrenceId eventType=$eventType " +
                        "boundaryEpoch=$boundaryEpoch receivedSystemWallTime=$receivedSystemWallTime",
                )
                // 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik END
                val runtime = PraktikaRuntimeHolder.get(context)
                val reason = when (eventType) {
                    BoundaryEventType.PLANNED_BOUNDARY.name -> NotificationSyncReason.PLANNED_ALARM
                    BoundaryEventType.EXPIRY_BOUNDARY.name -> NotificationSyncReason.EXPIRY_ALARM
                    else -> NotificationSyncReason.PLANNED_ALARM
                }
                runtime.notificationCoordinator.sync(reason)
                // 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik START - post-sync diagnostic log
                val statusAfterSync = if (occurrenceId > 0L) {
                    runtime.database.questionOccurrenceDao().getById(occurrenceId)?.status?.name
                } else {
                    null
                }
                Log.i(
                    TAG,
                    "syncComplete reason=$reason occurrenceId=$occurrenceId statusAfterSync=$statusAfterSync",
                )
                // 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik END
            } catch (exception: Exception) {
                Log.e(TAG, "Alarm receiver failed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_BOUNDARY_EPOCH_MILLIS = "boundary_epoch_millis"
        const val EXTRA_PLANNED_AT_EPOCH_MILLIS = "planned_at_epoch_millis"
        const val TAG = "PracticeAlarmReceiver"
        private const val INVALID_ID = -1L
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
