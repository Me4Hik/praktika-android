package com.me4hik.praktika.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AndroidPracticeNotificationPresenterTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var presenter: AndroidPracticeNotificationPresenter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        presenter = AndroidPracticeNotificationPresenter(context, notificationManager)
        notificationManager.cancelAll()
    }

    @Test
    fun postingAThenB_usesSameStableOsNotificationId() {
        presenter.showNotification(showPlan(occurrenceId = 41L, text = "A"))
        val afterA = practiceNotifications()
        assertEquals(1, afterA.size)
        assertEquals(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID, afterA.single().id)

        presenter.showNotification(showPlan(occurrenceId = 42L, text = "B"))
        val afterB = practiceNotifications()
        assertEquals(1, afterB.size)
        assertEquals(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID, afterB.single().id)
        assertEquals(
            AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID,
            AndroidPracticeNotificationPresenter.notificationId(41L),
        )
        assertEquals(
            AndroidPracticeNotificationPresenter.notificationId(41L),
            AndroidPracticeNotificationPresenter.notificationId(42L),
        )
    }

    @Test
    fun replacingAWithB_updatesPendingIntentToOccurrenceB() {
        presenter.showNotification(showPlan(occurrenceId = 41L, plannedAt = 1_000L, text = "A"))
        presenter.showNotification(showPlan(occurrenceId = 42L, plannedAt = 2_000L, text = "B"))

        val posted = practiceNotifications().single()
        val tapIntent = Shadows.shadowOf(posted.notification.contentIntent).savedIntent
        assertEquals(42L, tapIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, -1L))
        assertEquals(2_000L, tapIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, -1L))
        assertNotEquals(
            AndroidPracticeNotificationPresenter.tapRequestCode(41L),
            AndroidPracticeNotificationPresenter.tapRequestCode(42L),
        )
    }

    @Test
    fun legacyPracticeIdsAreCancelled_stableIdIsPreserved_unrelatedIsUntouched() {
        postRaw(tag = AndroidPracticeNotificationPresenter.NOTIFICATION_TAG, id = 41)
        postRaw(tag = AndroidPracticeNotificationPresenter.NOTIFICATION_TAG, id = 42)
        presenter.showNotification(showPlan(occurrenceId = 99L, text = "current"))
        postRaw(tag = "other_tag", id = 7)

        presenter.cancelLegacyPracticeNotifications(currentOccurrenceId = 99L, syncReason = "APP_START")

        val remaining = notificationManager.activeNotifications.toList()
        val practice = remaining.filter { it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG }
        assertEquals(listOf(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID), practice.map { it.id })
        assertTrue(remaining.any { it.tag == "other_tag" && it.id == 7 })
        assertTrue(remaining.none { it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG && it.id == 41 })
        assertTrue(remaining.none { it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG && it.id == 42 })
    }

    @Test
    fun repeatedLegacyCleanup_isIdempotent() {
        postRaw(tag = AndroidPracticeNotificationPresenter.NOTIFICATION_TAG, id = 41)
        presenter.showNotification(showPlan(occurrenceId = 7L, text = "current"))

        presenter.cancelLegacyPracticeNotifications()
        presenter.cancelLegacyPracticeNotifications()

        assertEquals(1, practiceNotifications().size)
        assertEquals(
            AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID,
            practiceNotifications().single().id,
        )
    }

    @Test
    fun activeNotificationEnumerationFailure_doesNotBlockCurrentShow() {
        val failingPresenter = AndroidPracticeNotificationPresenter(
            context = context,
            notificationManager = notificationManager,
            activeNotificationsProvider = { error("enumeration failed") },
        )

        failingPresenter.cancelLegacyPracticeNotifications(currentOccurrenceId = 8L, syncReason = "APP_START")
        failingPresenter.showNotification(showPlan(occurrenceId = 8L, text = "current"))

        val posted = practiceNotifications()
        assertEquals(1, posted.size)
        assertEquals(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID, posted.single().id)
        val tapIntent = Shadows.shadowOf(posted.single().notification.contentIntent).savedIntent
        assertEquals(8L, tapIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, -1L))
    }

    @Test
    fun cancelCurrent_removesStableNotificationOnly() {
        postRaw(tag = "other_tag", id = 7)
        presenter.showNotification(showPlan(occurrenceId = 5L, text = "current"))

        presenter.cancelCurrentPracticeNotification()

        assertTrue(practiceNotifications().isEmpty())
        assertTrue(notificationManager.activeNotifications.any { it.tag == "other_tag" && it.id == 7 })
    }

    @Test
    fun cancelAllPractice_isTagScoped() {
        postRaw(tag = AndroidPracticeNotificationPresenter.NOTIFICATION_TAG, id = 41)
        presenter.showNotification(showPlan(occurrenceId = 5L, text = "current"))
        postRaw(tag = "other_tag", id = 7)

        presenter.cancelAllPracticeNotifications()

        assertTrue(practiceNotifications().isEmpty())
        assertTrue(notificationManager.activeNotifications.any { it.tag == "other_tag" && it.id == 7 })
    }

    @Test
    fun findActive_readsOccurrenceFromStableNotificationExtras() {
        presenter.showNotification(showPlan(occurrenceId = 77L, text = "current"))
        assertEquals(77L, presenter.findActivePracticeNotificationOccurrenceId())
        assertEquals(PracticeNotificationKind.QUESTION, presenter.findActivePracticeNotificationKind())
    }

    @Test
    fun snoozedNotification_usesSameTagId_andRepeatCopy() {
        presenter.showNotification(
            showPlan(
                occurrenceId = 10L,
                plannedAt = 1_000L,
                text = "Q",
                kind = PracticeNotificationKind.SNOOZED,
                deferredUntil = 1_800_000L,
                zoneId = "UTC",
            ),
        )
        val posted = practiceNotifications().single()
        assertEquals(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID, posted.id)
        assertEquals(AndroidPracticeNotificationPresenter.NOTIFICATION_TAG, posted.tag)
        assertEquals(
            PracticeNotificationKind.SNOOZED,
            presenter.findActivePracticeNotificationKind(),
        )
        val expectedBody = context.getString(
            com.me4hik.praktika.R.string.notification_snoozed_body,
            SnoozedNotificationCopy.formatRepeatAt(1_800_000L, "UTC"),
        )
        assertEquals(expectedBody, posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(1, posted.notification.actions?.size ?: 0)
        assertEquals(
            context.getString(com.me4hik.praktika.R.string.notification_action_answer_now),
            posted.notification.actions!![0].title.toString(),
        )

        val bodyIntent = Shadows.shadowOf(posted.notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, bodyIntent.component?.className)
        assertEquals(10L, bodyIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, -1L))
        assertEquals(1_000L, bodyIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, -1L))
        assertEquals(
            MainActivity.NOTIFICATION_SOURCE_VALUE,
            bodyIntent.getStringExtra(MainActivity.EXTRA_NOTIFICATION_SOURCE),
        )

        val answerNowIntent = Shadows.shadowOf(posted.notification.actions!![0].actionIntent).savedIntent
        assertEquals(MainActivity::class.java.name, answerNowIntent.component?.className)
        assertEquals(10L, answerNowIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, -1L))
        assertEquals(1_000L, answerNowIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_PLANNED_AT, -1L))
        assertEquals(
            MainActivity.NOTIFICATION_SOURCE_VALUE,
            answerNowIntent.getStringExtra(MainActivity.EXTRA_NOTIFICATION_SOURCE),
        )
        assertEquals(PracticeNotificationChannels.SNOOZED, posted.notification.channelId)
    }

    @Test
    fun dueQuestion_keepsAnswerAndDeferActions() {
        presenter.showNotification(
            showPlan(occurrenceId = 5L, plannedAt = 2_000L, text = "due question"),
        )
        val posted = practiceNotifications().single()
        assertEquals(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID, posted.id)
        assertEquals(AndroidPracticeNotificationPresenter.NOTIFICATION_TAG, posted.tag)
        assertEquals(2, posted.notification.actions?.size ?: 0)
        assertEquals(
            context.getString(com.me4hik.praktika.R.string.notification_action_answer),
            posted.notification.actions!![0].title.toString(),
        )
        assertEquals(
            context.getString(com.me4hik.praktika.R.string.notification_action_defer),
            posted.notification.actions!![1].title.toString(),
        )
        val answerIntent = Shadows.shadowOf(posted.notification.actions!![0].actionIntent).savedIntent
        assertEquals(MainActivity::class.java.name, answerIntent.component?.className)
        assertEquals(5L, answerIntent.getLongExtra(MainActivity.EXTRA_NOTIFICATION_OCCURRENCE_ID, -1L))
        val deferIntent = Shadows.shadowOf(posted.notification.actions!![1].actionIntent).savedIntent
        assertEquals(
            NotificationDeferActionReceiver::class.java.name,
            deferIntent.component?.className,
        )
    }

    @Test
    fun dueQuestion_usesHighImportanceChannel() {
        presenter.ensureChannelsCreated()
        presenter.showNotification(showPlan(occurrenceId = 3L, text = "due", soundEnabled = true))
        val posted = practiceNotifications().single()
        assertEquals(PracticeNotificationChannels.DUE_SOUND, posted.notification.channelId)
        val channel = notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SOUND)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        presenter.showNotification(showPlan(occurrenceId = 3L, text = "due", soundEnabled = false))
        val silentPosted = practiceNotifications().single()
        assertEquals(PracticeNotificationChannels.DUE_SILENT, silentPosted.notification.channelId)
        val silentChannel = notificationManager.getNotificationChannel(PracticeNotificationChannels.DUE_SILENT)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, silentChannel.importance)
    }

    @Test
    fun snoozedThenDue_refreshesSameIdToQuestion() {
        presenter.showNotification(
            showPlan(
                occurrenceId = 10L,
                text = "Q",
                kind = PracticeNotificationKind.SNOOZED,
                deferredUntil = 1_800L,
            ),
        )
        presenter.showNotification(showPlan(occurrenceId = 10L, text = "Q again"))
        val posted = practiceNotifications().single()
        assertEquals(AndroidPracticeNotificationPresenter.PRACTICE_NOTIFICATION_ID, posted.id)
        assertEquals(PracticeNotificationKind.QUESTION, presenter.findActivePracticeNotificationKind())
        assertEquals("Q again", posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(PracticeNotificationChannels.DUE_SILENT, posted.notification.channelId)
    }

    private fun showPlan(
        occurrenceId: Long,
        plannedAt: Long = 1_000L,
        text: String,
        soundEnabled: Boolean = false,
        kind: PracticeNotificationKind = PracticeNotificationKind.QUESTION,
        deferredUntil: Long? = null,
        zoneId: String = "UTC",
    ): NotificationShowPlan {
        return NotificationShowPlan(
            occurrenceId = occurrenceId,
            plannedAtEpochMillis = plannedAt,
            questionTextSnapshot = text,
            soundEnabled = soundEnabled,
            kind = kind,
            deferredUntilEpochMillis = deferredUntil,
            zoneId = zoneId,
        )
    }

    private fun postRaw(tag: String, id: Int) {
        val notification = NotificationCompat.Builder(context, "raw")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("raw")
            .setContentText("raw")
            .build()
        notificationManager.notify(tag, id, notification)
    }

    private fun practiceNotifications(): List<StatusBarNotification> {
        return notificationManager.activeNotifications.filter {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG
        }
    }
}
