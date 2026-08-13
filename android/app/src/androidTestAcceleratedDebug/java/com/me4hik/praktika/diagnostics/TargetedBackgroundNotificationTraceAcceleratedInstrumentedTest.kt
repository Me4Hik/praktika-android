// 10.08.2026 Post-release fixes cursor by Me4Hik START - targeted background notification trace
package com.me4hik.praktika.diagnostics

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.Stage12BlackviewE2ESupport
import com.me4hik.praktika.Stage12RealAlarmProofSupport
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsComposeTestSupport
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class TargetedBackgroundNotificationTraceAcceleratedInstrumentedTest {
    private val devicePreflightRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
                base.evaluate()
            }
        }
    }

    private val sandboxReset = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                resetAcceleratedSandbox()
                try {
                    base.evaluate()
                } finally {
                    PraktikaRuntimeHolder.resetForTests()
                    MainActivityInitGate.resetForTests()
                }
            }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(devicePreflightRule)
        .around(sandboxReset)
        .around(composeRule)

    @Test(timeout = 600_000)
    fun backgroundNotificationTraceRecordsScheduleAlarmAndDeliveryChain() {
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12BlackviewE2ESupport.grantPostNotificationsViaShell()
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        Stage12RealAlarmProofSupport.prepareWallSyncClock1x(runtime)
        runBlocking { runtime.cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_SETTINGS)
        }

        val slotMinutes = Stage12RealAlarmProofSupport.slotMinutesTwoToThreeAhead(runtime)
        val seqBeforeSchedule = TargetedTraceTestSupport.readEvents(context).maxOfOrNull { it.seq } ?: 0L

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, slotMinutes)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        composeRule.waitForIdle()

        val scheduleChanged = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "schedule_changed",
            afterSeq = seqBeforeSchedule,
        )
        checkNotNull(scheduleChanged)
        assertTrue(scheduleChanged.metadata["slot_minutes_after"]?.contains(slotMinutes.toString()) == true)

        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        assertEquals(QuestionOccurrenceStatus.SCHEDULED, occurrence.status)
        assertTrue(occurrence.plannedAtEpochMillis > System.currentTimeMillis())

        runBlocking {
            runtime.notificationCoordinator.sync(NotificationSyncReason.MUTATION)
        }

        val alarmScheduled = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "notification_alarm_scheduled",
            afterSeq = seqBeforeSchedule,
            predicate = {
                it.metadata["occurrence_id"] == occurrence.id.toString() &&
                    it.metadata["alarm_type"] == "planned"
            },
        )
        checkNotNull(alarmScheduled)
        assertEquals("planned", alarmScheduled.metadata["alarm_type"])
        val requestedTrigger = alarmScheduled.metadata["trigger_at_epoch_ms"]!!.toLong()
        assertEquals(AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS.toString(), alarmScheduled.metadata["window_ms"])
        assertEquals("setWindow", alarmScheduled.metadata["scheduler_api"])

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertTrue(TargetedTraceTestSupport.eventNames(context).contains("app_backgrounded"))

        TargetedTraceTestSupport.exportDiagnosticsToExternal(context, "background_pre_alarm")
        TargetedTraceTestSupport.logPrompt124Trace(
            tag = "PROMPT124_BACKGROUND_PRE_ALARM",
            context = context,
            eventNames = setOf(
                "schedule_changed",
                "notification_sync_started",
                "notification_sync_result",
                "notification_alarm_scheduled",
                "app_backgrounded",
            ),
        )

        val deadline = requestedTrigger + AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS +
            TimeUnit.MINUTES.toMillis(1)
        var firedAt: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val fired = TargetedTraceTestSupport.eventsAfter(
                context,
                "notification_alarm_fired",
                seqBeforeSchedule,
            ).lastOrNull { it.metadata["occurrence_id"] == occurrence.id.toString() }
            if (fired != null) {
                firedAt = fired.metadata["received_at_epoch_ms"]!!.toLong()
                break
            }
            Thread.sleep(2_000)
        }

        val alarmFired = TargetedTraceTestSupport.eventsAfter(
            context,
            "notification_alarm_fired",
            seqBeforeSchedule,
        ).lastOrNull { it.metadata["occurrence_id"] == occurrence.id.toString() }
        checkNotNull(alarmFired) {
            "notification_alarm_fired missing for occurrence ${occurrence.id} before deadline $deadline"
        }
        assertNotNull(firedAt)
        val delayMs = firedAt!! - requestedTrigger
        android.util.Log.i(
            TAG,
            "alarm_timing requested_trigger=$requestedTrigger actual_fire=$firedAt delay_ms=$delayMs " +
                "window_ms=${AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS}",
        )

        composeRule.waitUntil(timeoutMillis = 30_000) {
            TargetedTraceTestSupport.eventNames(context).contains("notification_sync_started") &&
                TargetedTraceTestSupport.readEvents(context).any {
                    it.name == "notification_sync_started" &&
                        it.seq > seqBeforeSchedule &&
                        it.metadata["reason"] == NotificationSyncReason.PLANNED_ALARM.name
                }
        }

        val syncResult = TargetedTraceTestSupport.eventsAfter(
            context,
            "notification_sync_result",
            seqBeforeSchedule,
        ).lastOrNull { it.metadata["reason"] == NotificationSyncReason.PLANNED_ALARM.name }
        checkNotNull(syncResult)
        assertEquals(occurrence.id.toString(), syncResult.metadata["occurrence_id"])

        val postAttempt = TargetedTraceTestSupport.eventsAfter(
            context,
            "notification_post_attempt",
            seqBeforeSchedule,
        ).lastOrNull { it.metadata["occurrence_id"] == occurrence.id.toString() }
        checkNotNull(postAttempt)

        val postResult = TargetedTraceTestSupport.eventsAfter(
            context,
            "notification_post_result",
            seqBeforeSchedule,
        ).lastOrNull {
            it.metadata["occurrence_id"] == occurrence.id.toString() &&
                it.metadata["result"] == "posted"
        }
        checkNotNull(postResult)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertTrue(hasActiveNotification(context, occurrence.id))

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        TargetedTraceTestSupport.submitManualReport(
            composeRule = composeRule,
            comment = "TARGETED BACKGROUND NOTIFICATION TRACE TEST delay_ms=$delayMs",
        )

        assertTrue(
            TargetedTraceTestSupport.reportContainsEventNames(
                context,
                "schedule_changed",
                "notification_alarm_scheduled",
                "notification_alarm_fired",
                "notification_post_result",
            ),
        )
        val report = TargetedTraceTestSupport.latestReport(context)!!
        assertFalse(report.has("question_text"))
        assertFalse(report.has("answer_text"))
        assertTrue(report.optString("testerComment").contains("TARGETED BACKGROUND NOTIFICATION TRACE TEST"))

        TargetedTraceTestSupport.exportDiagnosticsToExternal(context, "background")
        TargetedTraceTestSupport.logPrompt124Trace(
            tag = "PROMPT124_BACKGROUND_TRACE",
            context = context,
            eventNames = setOf(
                "schedule_changed",
                "schedule_change_result",
                "notification_sync_started",
                "notification_sync_result",
                "notification_alarm_scheduled",
                "notification_alarm_cancelled",
                "notification_alarm_fired",
                "notification_post_attempt",
                "notification_post_result",
                "app_backgrounded",
                "app_foregrounded",
            ),
        )
    }

    private fun hasActiveNotification(context: Context, occurrenceId: Long): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        Stage12BlackviewE2ESupport.revokePostNotificationsViaShell()
        Stage12BlackviewE2ESupport.clearNotificationPermissionFlag()
        context.filesDir.listFiles()
            ?.filter {
                it.name == AcceleratedTimeStorage.STATE_FILE_NAME ||
                    it.name == "diagnostics"
            }
            ?.forEach { entry ->
                if (entry.isDirectory) {
                    entry.deleteRecursively()
                } else {
                    entry.delete()
                }
            }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    private companion object {
        const val TAG = "TargetedBgNotifTrace"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
