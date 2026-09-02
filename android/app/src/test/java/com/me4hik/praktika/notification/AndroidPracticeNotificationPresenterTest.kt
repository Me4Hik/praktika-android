package com.me4hik.praktika.notification

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
    }

    private fun showPlan(
        occurrenceId: Long,
        plannedAt: Long = 1_000L,
        text: String,
    ): NotificationShowPlan {
        return NotificationShowPlan(
            occurrenceId = occurrenceId,
            plannedAtEpochMillis = plannedAt,
            questionTextSnapshot = text,
            soundEnabled = false,
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
