// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - channels и presenter
package com.me4hik.praktika.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.R
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics
import kotlin.coroutines.cancellation.CancellationException

object PracticeNotificationChannels {
    const val SOUND = "practice_sound"
    const val SILENT = "practice_silent"
}

class AndroidPracticeNotificationPresenter(
    private val context: Context,
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
    private val activeNotificationsProvider: () -> Array<out StatusBarNotification> = {
        notificationManager.activeNotifications
    },
) : PracticeNotificationPresenter {

    override fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val soundChannel = NotificationChannel(
            PracticeNotificationChannels.SOUND,
            context.getString(R.string.notification_channel_sound_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_sound_description)
            enableVibration(true)
        }
        val silentChannel = NotificationChannel(
            PracticeNotificationChannels.SILENT,
            context.getString(R.string.notification_channel_silent_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_silent_description)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(soundChannel)
        notificationManager.createNotificationChannel(silentChannel)
    }

    override fun findActivePracticeNotificationOccurrenceId(): Long? {
        val practiceNotifications = loadActiveNotificationsOrEmpty()
            .filter { status -> status.tag == NOTIFICATION_TAG }
        val preferred = practiceNotifications.firstOrNull { it.id == PRACTICE_NOTIFICATION_ID }
            ?: practiceNotifications.firstOrNull()
        return preferred?.let { occurrenceIdFrom(it) }
    }

    override fun showNotification(plan: NotificationShowPlan) {
        ensureChannelsCreated()
        val channelId = if (plan.soundEnabled) {
            PracticeNotificationChannels.SOUND
        } else {
            PracticeNotificationChannels.SILENT
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            tapRequestCode(plan.occurrenceId),
            tapIntent(plan),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val identityExtras = Bundle().apply {
            putLong(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, plan.occurrenceId)
            putLong(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, plan.plannedAtEpochMillis)
        }
        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_public_title))
            .setContentText(context.getString(R.string.notification_public_body))
            .setSmallIcon(R.drawable.ic_notification_practice)
            .build()
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(plan.questionTextSnapshot)
            .setStyle(NotificationCompat.BigTextStyle().bigText(plan.questionTextSnapshot))
            .setSmallIcon(R.drawable.ic_notification_practice)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setWhen(plan.plannedAtEpochMillis)
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .addExtras(identityExtras)
            .build()
        TargetedBugDiagnostics.recordNotificationPostAttempt(
            occurrenceId = plan.occurrenceId,
            channelId = channelId,
            capability = TargetedBugDiagnostics.NotificationTraceContext.deliveryCapability,
        )
        try {
            notificationManager.notify(NOTIFICATION_TAG, PRACTICE_NOTIFICATION_ID, notification)
            TargetedBugDiagnostics.recordNotificationPostResult(
                occurrenceId = plan.occurrenceId,
                result = "posted",
            )
        } catch (exception: Exception) {
            TargetedBugDiagnostics.recordNotificationPostResult(
                occurrenceId = plan.occurrenceId,
                result = "failed",
                exceptionClass = exception.javaClass.simpleName,
            )
            throw exception
        }
    }

    override fun cancelCurrentPracticeNotification() {
        notificationManager.cancel(NOTIFICATION_TAG, PRACTICE_NOTIFICATION_ID)
    }

    override fun cancelLegacyPracticeNotifications(
        currentOccurrenceId: Long?,
        syncReason: String?,
    ) {
        val active = loadActiveNotificationsOrEmpty(currentOccurrenceId, syncReason)
        for (status in active) {
            if (status.tag != NOTIFICATION_TAG) {
                continue
            }
            if (status.id == PRACTICE_NOTIFICATION_ID) {
                continue
            }
            try {
                notificationManager.cancel(NOTIFICATION_TAG, status.id)
                recordLegacyCancel(
                    legacyNotificationId = status.id,
                    currentOccurrenceId = currentOccurrenceId,
                    syncReason = syncReason,
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                recordCleanupFailure(
                    exception = exception,
                    currentOccurrenceId = currentOccurrenceId,
                    syncReason = syncReason,
                    legacyNotificationId = status.id,
                )
            }
        }
    }

    override fun cancelAllPracticeNotifications() {
        val active = loadActiveNotificationsOrEmpty()
        for (status in active) {
            if (status.tag != NOTIFICATION_TAG) {
                continue
            }
            try {
                notificationManager.cancel(NOTIFICATION_TAG, status.id)
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }
                recordCleanupFailure(exception = exception)
            }
        }
    }

    private fun tapIntent(plan: NotificationShowPlan): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, plan.occurrenceId)
            putExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, plan.plannedAtEpochMillis)
            putExtra(MainActivity.EXTRA_NOTIFICATION_SOURCE, MainActivity.NOTIFICATION_SOURCE_VALUE)
        }
    }

    private fun loadActiveNotificationsOrEmpty(
        currentOccurrenceId: Long? = null,
        syncReason: String? = null,
    ): List<StatusBarNotification> {
        return try {
            activeNotificationsProvider()?.toList().orEmpty()
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            recordCleanupFailure(
                exception = exception,
                currentOccurrenceId = currentOccurrenceId,
                syncReason = syncReason,
            )
            emptyList()
        }
    }

    private fun occurrenceIdFrom(status: StatusBarNotification): Long? {
        val extras = status.notification.extras
        if (extras.containsKey(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID)) {
            val extrasId = extras.getLong(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID)
            if (extrasId > 0L) {
                return extrasId
            }
        }
        if (status.id != PRACTICE_NOTIFICATION_ID && status.id > 0) {
            return status.id.toLong()
        }
        return null
    }

    private fun recordLegacyCancel(
        legacyNotificationId: Int,
        currentOccurrenceId: Long?,
        syncReason: String?,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.NOTIFICATION,
            name = "PRACTICE_NOTIFICATION_LEGACY_CANCEL",
            metadata = buildMap {
                put("legacy_notification_id", legacyNotificationId.toString())
                put("tag", NOTIFICATION_TAG)
                currentOccurrenceId?.let { put("current_occurrence_id", it.toString()) }
                syncReason?.let { put("sync_reason", it) }
            },
        )
    }

    private fun recordCleanupFailure(
        exception: Exception,
        currentOccurrenceId: Long? = null,
        syncReason: String? = null,
        legacyNotificationId: Int? = null,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.NOTIFICATION,
            name = "PRACTICE_NOTIFICATION_LEGACY_CLEANUP_FAILED",
            metadata = buildMap {
                put("tag", NOTIFICATION_TAG)
                put("exception_class", exception.javaClass.simpleName)
                currentOccurrenceId?.let { put("current_occurrence_id", it.toString()) }
                syncReason?.let { put("sync_reason", it) }
                legacyNotificationId?.let { put("legacy_notification_id", it.toString()) }
            },
        )
    }

    companion object {
        const val NOTIFICATION_TAG = "practice_question"
        const val PRACTICE_NOTIFICATION_ID = 1001

        fun notificationId(@Suppress("UNUSED_PARAMETER") occurrenceId: Long): Int {
            return PRACTICE_NOTIFICATION_ID
        }

        fun tapRequestCode(occurrenceId: Long): Int = ("practice_tap_$occurrenceId").hashCode()
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
