// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - channels и presenter
package com.me4hik.praktika.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.R
import com.me4hik.praktika.diagnostics.TargetedBugDiagnostics

object PracticeNotificationChannels {
    const val SOUND = "practice_sound"
    const val SILENT = "practice_silent"
}

class AndroidPracticeNotificationPresenter(
    private val context: Context,
) : PracticeNotificationPresenter {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
        return notificationManager.activeNotifications
            .firstOrNull { status ->
                status.tag == NOTIFICATION_TAG
            }
            ?.id
            ?.toLong()
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
            .build()
        TargetedBugDiagnostics.recordNotificationPostAttempt(
            occurrenceId = plan.occurrenceId,
            channelId = channelId,
            capability = TargetedBugDiagnostics.NotificationTraceContext.deliveryCapability,
        )
        try {
            notificationManager.notify(NOTIFICATION_TAG, notificationId(plan.occurrenceId), notification)
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

    override fun cancelPracticeNotification(occurrenceId: Long) {
        notificationManager.cancel(NOTIFICATION_TAG, notificationId(occurrenceId))
    }

    override fun cancelAllPracticeNotifications() {
        notificationManager.activeNotifications
            .filter { it.tag == NOTIFICATION_TAG }
            .forEach { status ->
                notificationManager.cancel(NOTIFICATION_TAG, status.id)
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

    companion object {
        const val NOTIFICATION_TAG = "practice_question"

        fun notificationId(occurrenceId: Long): Int = occurrenceId.toInt()

        fun tapRequestCode(occurrenceId: Long): Int = ("practice_tap_$occurrenceId").hashCode()
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
