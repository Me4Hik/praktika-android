// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt126 reopen-before-alarm experiment
package com.me4hik.praktika.diagnostics

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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
import org.json.JSONArray
import org.json.JSONObject

data class ReopenBeforeAlarmResult(
    val occurrenceId: Long,
    val requestedTriggerEpochMs: Long,
    val windowMs: Long,
    val alarmPendingBeforeBackground: Boolean,
    val atTPlus2Ms: Long,
    val alarmFiredBeforeReopen: Boolean,
    val alarmStillPendingBeforeReopen: Boolean,
    val notificationVisibleBeforeReopen: Boolean,
    val notificationPostedBeforeReopen: Boolean,
    val foregroundSyncPosted: Boolean,
    val notificationVisibleAfterReopen: Boolean,
    val alarmFiredAfterReopen: Boolean,
    val alarmStillPendingAfterReopen: Boolean,
    val realSymptomReproduced: Boolean,
    val foregroundReopenPostsBeforeAlarm: Boolean,
)

object ReopenBeforeAlarmExperimentSupport {
    const val TAG = "ReopenBeforeAlarm"
    const val BACKGROUND_METHOD = "ActivityScenario.moveToState(CREATED)"
    const val REOPEN_METHOD = "ActivityScenario.moveToState(RESUMED)"
    val WAIT_AFTER_TRIGGER_MS = TimeUnit.MINUTES.toMillis(2)
    const val PROMPT125_SETWINDOW_DELAY_MS = 600_013L

    private val traceEventNames = setOf(
        "schedule_changed",
        "notification_sync_started",
        "notification_sync_result",
        "notification_alarm_scheduled",
        "notification_alarm_cancelled",
        "notification_alarm_fired",
        "notification_post_attempt",
        "notification_post_result",
        "app_backgrounded",
        "app_foregrounded",
        "permission_ui_refresh_trigger",
    )

    fun resetSandbox() {
        AndroidAlarmScheduler.experimentApiModeOverride = AlarmSchedulerApiMode.SET_WINDOW
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        Stage12BlackviewE2ESupport.clearNotificationPermissionFlag()
        context.filesDir.listFiles()
            ?.filter {
                it.name == AcceleratedTimeStorage.STATE_FILE_NAME || it.name == "diagnostics"
            }
            ?.forEach { entry ->
                if (entry.isDirectory) entry.deleteRecursively() else entry.delete()
            }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    fun shellDump(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
    }

    fun pkg(): String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    fun filterAlarmDump(raw: String): String {
        return raw.lines().filter { it.contains(pkg(), ignoreCase = true) }.joinToString("\n")
    }

    fun hasPendingPlannedAlarm(raw: String, triggerEpochMs: Long): Boolean {
        if (!raw.contains(pkg())) return false
        return raw.contains("RTC_WAKEUP", ignoreCase = true) &&
            raw.contains(triggerEpochMs.toString())
    }

    fun hasActiveNotification(context: Context, occurrenceId: Long): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
    }

    fun runReopenExperiment(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ): ReopenBeforeAlarmResult {
        AndroidAlarmScheduler.experimentApiModeOverride = AlarmSchedulerApiMode.SET_WINDOW
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

        val seqBefore = TargetedTraceTestSupport.readEvents(context).maxOfOrNull { it.seq } ?: 0L
        val slotMinutes = Stage12RealAlarmProofSupport.slotMinutesTwoToThreeAhead(runtime)

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, slotMinutes)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        composeRule.waitForIdle()

        checkNotNull(
            TargetedTraceTestSupport.waitForEventAfter(context, "schedule_changed", seqBefore),
        )

        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        check(occurrence.status == QuestionOccurrenceStatus.SCHEDULED)

        runBlocking { runtime.notificationCoordinator.sync(NotificationSyncReason.MUTATION) }

        val alarmScheduled = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "notification_alarm_scheduled",
            afterSeq = seqBefore,
            predicate = {
                it.metadata["occurrence_id"] == occurrence.id.toString() &&
                    it.metadata["alarm_type"] == "planned" &&
                    it.metadata["scheduler_api"] == "setWindow"
            },
        )
        checkNotNull(alarmScheduled)
        val trigger = alarmScheduled.metadata["trigger_at_epoch_ms"]!!.toLong()
        val windowMs = alarmScheduled.metadata["window_ms"]!!.toLong()
        check(windowMs == AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS)

        val dumpsysBeforeBg = shellDump("dumpsys alarm")
        val alarmPendingBeforeBg = hasPendingPlannedAlarm(dumpsysBeforeBg, trigger)
        check(alarmPendingBeforeBg)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        check(TargetedTraceTestSupport.eventNames(context).contains("app_backgrounded"))

        val reopenAt = trigger + WAIT_AFTER_TRIGGER_MS
        while (System.currentTimeMillis() < trigger) {
            Thread.sleep(500)
        }
        while (System.currentTimeMillis() < reopenAt) {
            Thread.sleep(500)
        }
        val atTPlus2 = System.currentTimeMillis()

        val alarmFiredBefore = TargetedTraceTestSupport.eventsAfter(
            context, "notification_alarm_fired", seqBefore,
        ).any { it.metadata["occurrence_id"] == occurrence.id.toString() }

        val postBefore = TargetedTraceTestSupport.eventsAfter(
            context, "notification_post_result", seqBefore,
        ).any {
            it.metadata["occurrence_id"] == occurrence.id.toString() &&
                it.metadata["result"] == "posted"
        }

        val visibleBefore = hasActiveNotification(context, occurrence.id)
        val dumpsysBeforeReopen = shellDump("dumpsys alarm")
        val alarmStillPending = hasPendingPlannedAlarm(dumpsysBeforeReopen, trigger)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            TargetedTraceTestSupport.readEvents(context).any {
                it.seq > seqBefore &&
                    it.name == "notification_sync_started" &&
                    it.metadata["reason"] == NotificationSyncReason.FOREGROUND.name
            }
        }

        val foregroundSyncResult = TargetedTraceTestSupport.eventsAfter(
            context, "notification_sync_result", seqBefore,
        ).lastOrNull { it.metadata["reason"] == NotificationSyncReason.FOREGROUND.name }

        val postAfterReopen = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "notification_post_result",
            afterSeq = seqBefore,
            timeoutMillis = 15_000,
            predicate = {
                it.metadata["occurrence_id"] == occurrence.id.toString() &&
                    it.metadata["result"] == "posted"
            },
        )

        composeRule.waitForIdle()
        val visibleAfter = hasActiveNotification(context, occurrence.id)
        val dumpsysAfterReopen = shellDump("dumpsys alarm")
        val alarmPendingAfter = hasPendingPlannedAlarm(dumpsysAfterReopen, trigger)
        val alarmFiredAfter = TargetedTraceTestSupport.eventsAfter(
            context, "notification_alarm_fired", seqBefore,
        ).any { it.metadata["occurrence_id"] == occurrence.id.toString() }

        val foregroundPosted = postAfterReopen != null &&
            !postBefore &&
            (foregroundSyncResult?.metadata?.get("show_notification") == "true" ||
                foregroundSyncResult?.metadata?.get("occurrence_id") == occurrence.id.toString())

        val symptomReproduced = !alarmFiredBefore &&
            !visibleBefore &&
            !postBefore &&
            alarmStillPending &&
            postAfterReopen != null &&
            visibleAfter

        val result = ReopenBeforeAlarmResult(
            occurrenceId = occurrence.id,
            requestedTriggerEpochMs = trigger,
            windowMs = windowMs,
            alarmPendingBeforeBackground = alarmPendingBeforeBg,
            atTPlus2Ms = atTPlus2,
            alarmFiredBeforeReopen = alarmFiredBefore,
            alarmStillPendingBeforeReopen = alarmStillPending,
            notificationVisibleBeforeReopen = visibleBefore,
            notificationPostedBeforeReopen = postBefore,
            foregroundSyncPosted = foregroundPosted,
            notificationVisibleAfterReopen = visibleAfter,
            alarmFiredAfterReopen = alarmFiredAfter,
            alarmStillPendingAfterReopen = alarmPendingAfter,
            realSymptomReproduced = symptomReproduced,
            foregroundReopenPostsBeforeAlarm = symptomReproduced && !alarmFiredBefore,
        )

        exportArtifacts(context, result, dumpsysBeforeReopen, dumpsysAfterReopen, seqBefore)
        android.util.Log.i(TAG, "result symptom=$symptomReproduced trigger=$trigger atTPlus2=$atTPlus2")
        return result
    }

    fun runBackgroundOnlyControl(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ): JSONObject {
        AndroidAlarmScheduler.experimentApiModeOverride = AlarmSchedulerApiMode.SET_WINDOW
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

        val seqBefore = TargetedTraceTestSupport.readEvents(context).maxOfOrNull { it.seq } ?: 0L
        val slotMinutes = Stage12RealAlarmProofSupport.slotMinutesTwoToThreeAhead(runtime)
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, slotMinutes)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)

        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        runBlocking { runtime.notificationCoordinator.sync(NotificationSyncReason.MUTATION) }

        val alarmScheduled = TargetedTraceTestSupport.waitForEventAfter(
            context, "notification_alarm_scheduled", seqBefore,
        ) { it.metadata["alarm_type"] == "planned" && it.metadata["scheduler_api"] == "setWindow" }
        checkNotNull(alarmScheduled)
        val trigger = alarmScheduled.metadata["trigger_at_epoch_ms"]!!.toLong()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val deadline = trigger + AndroidAlarmScheduler.WINDOW_LENGTH_MILLIS + TimeUnit.MINUTES.toMillis(1)
        var firedAt: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val fired = TargetedTraceTestSupport.eventsAfter(context, "notification_alarm_fired", seqBefore)
                .lastOrNull { it.metadata["occurrence_id"] == occurrence.id.toString() }
            if (fired != null) {
                firedAt = fired.metadata["received_at_epoch_ms"]!!.toLong()
                break
            }
            Thread.sleep(2_000)
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        val visible = hasActiveNotification(context, occurrence.id)
        return JSONObject().apply {
            put("control", "background_only_no_reopen_at_T_plus_2")
            put("trigger", trigger)
            put("fired_at", firedAt ?: JSONObject.NULL)
            put("delay_ms", firedAt?.minus(trigger) ?: JSONObject.NULL)
            put("notification_visible", visible)
        }
    }

    private fun exportArtifacts(
        context: Context,
        result: ReopenBeforeAlarmResult,
        dumpsysBeforeReopen: String,
        dumpsysAfterReopen: String,
        seqBefore: Long,
    ) {
        val exportRoot = File(context.getExternalFilesDir(null), "prompt126")
        exportRoot.mkdirs()

        val traceJson = buildTraceJson(context, result, seqBefore)
        File(exportRoot, "reopen_before_alarm_trace.json").writeText(traceJson)
        File(exportRoot, "alarm_state_before_reopen.txt").writeText(filterAlarmDump(dumpsysBeforeReopen))
        File(exportRoot, "alarm_state_after_reopen.txt").writeText(filterAlarmDump(dumpsysAfterReopen))

        val beforeAfter = JSONObject().apply {
            put("before_reopen", JSONObject().apply {
                put("at_epoch_ms", result.atTPlus2Ms)
                put("ms_after_trigger", result.atTPlus2Ms - result.requestedTriggerEpochMs)
                put("alarm_fired", result.alarmFiredBeforeReopen)
                put("alarm_still_pending", result.alarmStillPendingBeforeReopen)
                put("notification_visible", result.notificationVisibleBeforeReopen)
                put("notification_posted", result.notificationPostedBeforeReopen)
            })
            put("after_reopen", JSONObject().apply {
                put("foreground_sync_posted", result.foregroundSyncPosted)
                put("notification_visible", result.notificationVisibleAfterReopen)
                put("alarm_fired", result.alarmFiredAfterReopen)
                put("alarm_still_pending", result.alarmStillPendingAfterReopen)
            })
        }
        File(exportRoot, "notification_state_before_after.json").writeText(beforeAfter.toString(2))

        val timeline = JSONObject().apply {
            put("real_report", JSONObject().apply {
                put("last_slot", "05:36")
                put("app_start", "05:38:25")
                put("delta_from_last_slot", "2m25s")
            })
            put("controlled_experiment", JSONObject().apply {
                put("wait_after_trigger_ms", WAIT_AFTER_TRIGGER_MS)
                put("prompt125_setwindow_delay_ms", PROMPT125_SETWINDOW_DELAY_MS)
            })
            put(
                "is_real_timeline_compatible_with_setwindow_delay",
                result.atTPlus2Ms - result.requestedTriggerEpochMs < PROMPT125_SETWINDOW_DELAY_MS,
            )
            put("is_foreground_reopen_explanation_proven", result.realSymptomReproduced)
        }
        File(exportRoot, "real_timeline_comparison.json").writeText(timeline.toString(2))

        File(exportRoot, "test_results.txt").writeText(buildTestResults(result))

        android.util.Log.i("PROMPT126_REOPEN_TRACE", traceJson)
        TargetedTraceTestSupport.exportDiagnosticsToExternal(context, "reopen", "prompt126")
    }

    private fun buildTraceJson(context: Context, result: ReopenBeforeAlarmResult, seqBefore: Long): String {
        val array = JSONArray()
        TargetedTraceTestSupport.readEvents(context)
            .filter { it.seq > seqBefore && it.name in traceEventNames }
            .forEach { event ->
                array.put(
                    JSONObject().apply {
                        put("seq", event.seq)
                        put("timestamp", event.tsEpochMs)
                        put("category", event.category.name)
                        put("event", event.name)
                        put("metadata", JSONObject(event.metadata))
                    },
                )
            }
        return JSONObject().apply {
            put("occurrence_id", result.occurrenceId)
            put("scheduler_api", "setWindow")
            put("requested_trigger_epoch_ms", result.requestedTriggerEpochMs)
            put("window_ms", result.windowMs)
            put("events", array)
            put("result", JSONObject().apply {
                put("real_symptom_reproduced", result.realSymptomReproduced)
                put("foreground_reopen_posts_before_alarm", result.foregroundReopenPostsBeforeAlarm)
            })
        }.toString(2)
    }

    private fun buildTestResults(result: ReopenBeforeAlarmResult): String = buildString {
        appendLine("PROMPT126 Reopen Before setWindow Alarm")
        appendLine("BACKGROUND_METHOD=$BACKGROUND_METHOD")
        appendLine("REOPEN_METHOD=$REOPEN_METHOD")
        appendLine("occurrence_id=${result.occurrenceId}")
        appendLine("trigger=${result.requestedTriggerEpochMs}")
        appendLine("AT_T_PLUS_2:")
        appendLine("  ALARM_FIRED=${result.alarmFiredBeforeReopen}")
        appendLine("  ALARM_STILL_PENDING=${result.alarmStillPendingBeforeReopen}")
        appendLine("  NOTIFICATION_VISIBLE=${result.notificationVisibleBeforeReopen}")
        appendLine("REAL_SYMPTOM_REPRODUCED=${result.realSymptomReproduced}")
        appendLine("FOREGROUND_REOPEN_POSTS_BEFORE_SETWINDOW_ALARM=${result.foregroundReopenPostsBeforeAlarm}")
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
