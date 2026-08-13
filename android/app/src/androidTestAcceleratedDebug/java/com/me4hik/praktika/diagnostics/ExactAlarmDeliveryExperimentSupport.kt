// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt127 exact alarm device tests
package com.me4hik.praktika.diagnostics

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.Stage12BlackviewE2ESupport
import com.me4hik.praktika.Stage12RealAlarmProofSupport
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.AlarmSchedulerApiMode
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.notification.ExactAlarmCapability
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsComposeTestSupport
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

object ExactAlarmDeliveryExperimentSupport {
    const val TAG = "ExactAlarmDelivery"

    fun resetSandbox() {
        AcceleratedDeviceTestHarness.resetSandboxFull()
    }

    fun resetRuntimeOnly() {
        AcceleratedDeviceTestHarness.resetRuntimeOnly()
    }

    fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
    }

    fun pkg(): String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    fun grantExactAlarmAccess() {
        shell("appops set ${pkg()} SCHEDULE_EXACT_ALARM allow")
    }

    fun revokeExactAlarmAccess() {
        shell("appops set ${pkg()} SCHEDULE_EXACT_ALARM deny")
    }

    fun canScheduleExactAlarms(): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun prepareScheduledAlarm(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ): ScheduledAlarmContext {
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
        TargetedTraceTestSupport.awaitDiagnosticsQuiescence(context)
        val seqBefore = TargetedTraceTestSupport.readEvents(context).maxOfOrNull { it.seq } ?: 0L
        val slotMinutes = Stage12RealAlarmProofSupport.slotMinutesTwoToThreeAhead(runtime)
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, slotMinutes)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        runBlocking { runtime.notificationCoordinator.sync(NotificationSyncReason.MUTATION) }
        val alarm = TargetedTraceTestSupport.waitForEventAfter(
            context,
            "notification_alarm_scheduled",
            seqBefore,
            timeoutMillis = 30_000,
        ) {
            it.metadata["alarm_type"] == "planned"
        }
        checkNotNull(alarm) {
            "notification_alarm_scheduled missing after schedule sync; recent events:\n" +
                TargetedTraceTestSupport.describeRecentEvents(context)
        }
        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        return ScheduledAlarmContext(
            seqBefore = seqBefore,
            occurrenceId = occurrence.id,
            triggerEpochMs = alarm.metadata["trigger_at_epoch_ms"]!!.toLong(),
            schedulerApi = alarm.metadata["scheduler_api"]!!,
            exactCapability = alarm.metadata["exact_alarm_capability"] ?: "",
        )
    }

    fun backgroundAndWaitForAlarm(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        ctx: ScheduledAlarmContext,
        waitUntilMs: Long,
    ): Long? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        val deadline = waitUntilMs
        var firedAt: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val fired = TargetedTraceTestSupport.eventsAfter(context, "notification_alarm_fired", ctx.seqBefore)
                .lastOrNull { it.metadata["occurrence_id"] == ctx.occurrenceId.toString() }
            if (fired != null) {
                firedAt = fired.metadata["received_at_epoch_ms"]!!.toLong()
                break
            }
            Thread.sleep(1_000)
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        return firedAt
    }

    fun hasActiveNotification(context: Context, occurrenceId: Long): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
    }

    fun exportResult(label: String, payload: JSONObject) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "prompt127")
        root.mkdirs()
        File(root, "$label.json").writeText(payload.toString(2))
        android.util.Log.i("PROMPT127_$label", payload.toString())
    }
}

data class ScheduledAlarmContext(
    val seqBefore: Long,
    val occurrenceId: Long,
    val triggerEpochMs: Long,
    val schedulerApi: String,
    val exactCapability: String,
)
