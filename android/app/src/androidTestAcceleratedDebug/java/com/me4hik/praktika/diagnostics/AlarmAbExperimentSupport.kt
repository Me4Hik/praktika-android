// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt125 alarm A/B experiment helpers
package com.me4hik.praktika.diagnostics

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.me4hik.praktika.MainActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
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

data class AlarmExperimentResult(
    val label: String,
    val schedulerApi: String,
    val occurrenceId: Long,
    val requestedTriggerEpochMs: Long,
    val windowMs: Long,
    val scheduleChangedAtEpochMs: Long?,
    val appBackgroundedAtEpochMs: Long?,
    val alarmScheduled: Boolean,
    val systemPendingBeforeTrigger: Boolean,
    val alarmFired: Boolean,
    val actualAlarmFiredAtEpochMs: Long?,
    val delayMs: Long?,
    val notificationPosted: Boolean,
    val notificationVisible: Boolean,
    val syncPlannedAlarm: Boolean,
    val classification: String,
)

object AlarmAbExperimentSupport {
    const val TAG = "AlarmAbExperiment"
    const val BACKGROUND_METHOD = "ActivityScenario.moveToState(CREATED)"
    const val FORCE_STOP_AFTER_SCHEDULE = false

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
    )

    fun resetSandbox() {
        AndroidAlarmScheduler.experimentApiModeOverride = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        // Do not pm revoke here — on Android 15 Blackview it kills the in-process instrumentation host.
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

    fun shellDump(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
    }

    fun packageName(): String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    fun filterAlarmDump(raw: String, pkg: String): String {
        val lines = raw.lines()
        val hits = lines.filter { it.contains(pkg, ignoreCase = true) }
        return if (hits.isEmpty()) {
            "NO_LINES_FOR_PACKAGE=$pkg\n--- sample ---\n${lines.take(80).joinToString("\n")}"
        } else {
            hits.joinToString("\n")
        }
    }

    fun hasPendingAlarmInDump(raw: String, pkg: String, triggerEpochMs: Long): Boolean {
        if (!raw.contains(pkg)) {
            return false
        }
        val triggerSec = triggerEpochMs / 1000
        return raw.contains("RTC_WAKEUP", ignoreCase = true) &&
            (
                raw.contains(triggerEpochMs.toString()) ||
                    raw.contains(triggerSec.toString()) ||
                    raw.contains("origWhen", ignoreCase = true)
                )
    }

    fun captureDevicePowerState(): String {
        val idle = shellDump("dumpsys deviceidle")
        val battery = shellDump("dumpsys battery")
        val usage = shellDump("dumpsys usagestats")
        val bucketLine = usage.lines()
            .filter { it.contains(packageName(), ignoreCase = true) || it.contains("standby", ignoreCase = true) }
            .take(40)
            .joinToString("\n")
        return buildString {
            appendLine("=== deviceidle ===")
            appendLine(idle.lines().take(120).joinToString("\n"))
            appendLine()
            appendLine("=== battery ===")
            appendLine(battery.lines().take(40).joinToString("\n"))
            appendLine()
            appendLine("=== usagestats (filtered) ===")
            appendLine(bucketLine.ifEmpty { "no package/bucket lines" })
        }
    }

    fun runExperiment(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        label: String,
        apiMode: AlarmSchedulerApiMode,
        expectedSchedulerApi: String,
    ): AlarmExperimentResult {
        AndroidAlarmScheduler.experimentApiModeOverride = apiMode
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
        val seqBefore = TargetedTraceTestSupport.readEvents(context).maxOfOrNull { it.seq } ?: 0L

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, slotMinutes)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        composeRule.waitForIdle()

        val scheduleChanged = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "schedule_changed",
            afterSeq = seqBefore,
        )
        checkNotNull(scheduleChanged)

        val occurrence = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        }
        check(occurrence.status == QuestionOccurrenceStatus.SCHEDULED)

        runBlocking {
            runtime.notificationCoordinator.sync(NotificationSyncReason.MUTATION)
        }

        val alarmScheduledEvent = TargetedTraceTestSupport.waitForEventAfter(
            context = context,
            name = "notification_alarm_scheduled",
            afterSeq = seqBefore,
            predicate = {
                it.metadata["occurrence_id"] == occurrence.id.toString() &&
                    it.metadata["alarm_type"] == "planned" &&
                    it.metadata["scheduler_api"] == expectedSchedulerApi
            },
        )
        checkNotNull(alarmScheduledEvent) {
            "Missing planned alarm with scheduler_api=$expectedSchedulerApi"
        }

        val requestedTrigger = alarmScheduledEvent.metadata["trigger_at_epoch_ms"]!!.toLong()
        val windowMs = alarmScheduledEvent.metadata["window_ms"]!!.toLong()

        val dumpsysBefore = shellDump("dumpsys alarm")
        val filteredBefore = filterAlarmDump(dumpsysBefore, packageName())
        val systemPendingBefore = hasPendingAlarmInDump(dumpsysBefore, packageName(), requestedTrigger)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        check(TargetedTraceTestSupport.eventNames(context).contains("app_backgrounded"))

        val appBackgroundedAt = TargetedTraceTestSupport.eventsAfter(context, "app_backgrounded", seqBefore)
            .lastOrNull()?.tsEpochMs

        val observationWindowMs = if (windowMs > 0L) {
            windowMs + TimeUnit.MINUTES.toMillis(1)
        } else {
            TimeUnit.MINUTES.toMillis(11)
        }
        val deadline = requestedTrigger + observationWindowMs
        var firedAt: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val fired = TargetedTraceTestSupport.eventsAfter(
                context,
                "notification_alarm_fired",
                seqBefore,
            ).lastOrNull { it.metadata["occurrence_id"] == occurrence.id.toString() }
            if (fired != null) {
                firedAt = fired.metadata["received_at_epoch_ms"]!!.toLong()
                break
            }
            Thread.sleep(2_000)
        }

        val dumpsysAfter = shellDump("dumpsys alarm")
        val filteredAfter = filterAlarmDump(dumpsysAfter, packageName())

        val alarmFired = firedAt != null
        val delayMs = firedAt?.let { it - requestedTrigger }
        val postResult = TargetedTraceTestSupport.eventsAfter(context, "notification_post_result", seqBefore)
            .lastOrNull {
                it.metadata["occurrence_id"] == occurrence.id.toString() &&
                    it.metadata["result"] == "posted"
            }
        val syncPlanned = TargetedTraceTestSupport.eventsAfter(context, "notification_sync_started", seqBefore)
            .any { it.metadata["reason"] == NotificationSyncReason.PLANNED_ALARM.name }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        val notificationVisible = hasActiveNotification(context, occurrence.id)

        val classification = classify(
            alarmScheduled = true,
            systemPendingBefore = systemPendingBefore,
            alarmFired = alarmFired,
            syncPlanned = syncPlanned,
            notificationPosted = postResult != null,
            notificationVisible = notificationVisible,
        )

        val result = AlarmExperimentResult(
            label = label,
            schedulerApi = expectedSchedulerApi,
            occurrenceId = occurrence.id,
            requestedTriggerEpochMs = requestedTrigger,
            windowMs = windowMs,
            scheduleChangedAtEpochMs = scheduleChanged.tsEpochMs,
            appBackgroundedAtEpochMs = appBackgroundedAt,
            alarmScheduled = true,
            systemPendingBeforeTrigger = systemPendingBefore,
            alarmFired = alarmFired,
            actualAlarmFiredAtEpochMs = firedAt,
            delayMs = delayMs,
            notificationPosted = postResult != null,
            notificationVisible = notificationVisible,
            syncPlannedAlarm = syncPlanned,
            classification = classification,
        )

        exportExperimentArtifacts(
            label = label,
            context = context,
            dumpsysBefore = filteredBefore,
            dumpsysAfter = filteredAfter,
            devicePowerState = captureDevicePowerState(),
            result = result,
        )
        TargetedTraceTestSupport.exportDiagnosticsToExternal(context, label, "prompt125")

        android.util.Log.i(
            TAG,
            "experiment_result label=$label api=$expectedSchedulerApi " +
                "pending=$systemPendingBefore fired=$alarmFired posted=${postResult != null} " +
                "visible=$notificationVisible classification=$classification delay_ms=$delayMs",
        )
        return result
    }

    private fun classify(
        alarmScheduled: Boolean,
        systemPendingBefore: Boolean,
        alarmFired: Boolean,
        syncPlanned: Boolean,
        notificationPosted: Boolean,
        notificationVisible: Boolean,
    ): String {
        if (!alarmScheduled) return "ALARM_NOT_SCHEDULED"
        if (!systemPendingBefore) return "ALARM_NOT_IN_DUMPSYS"
        if (!alarmFired) return "ALARM_SCHEDULED_NOT_FIRED"
        if (!syncPlanned) return "ALARM_FIRED_SYNC_FAILED"
        if (!notificationPosted) return "SYNC_OK_NOTIFICATION_NOT_POSTED"
        if (!notificationVisible) return "NOTIFICATION_POSTED_NOT_VISIBLE"
        return "DELIVERY_COMPLETE"
    }

    private fun hasActiveNotification(context: Context, occurrenceId: Long): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.tag == AndroidPracticeNotificationPresenter.NOTIFICATION_TAG &&
                it.id == AndroidPracticeNotificationPresenter.notificationId(occurrenceId)
        }
    }

    fun exportExperimentArtifacts(
        label: String,
        context: Context,
        dumpsysBefore: String,
        dumpsysAfter: String,
        devicePowerState: String,
        result: AlarmExperimentResult,
    ) {
        val exportRoot = File(context.getExternalFilesDir(null), "prompt125")
        exportRoot.mkdirs()
        val traceJson = traceJson(context, result)
        File(exportRoot, "${label}_trace.json").writeText(traceJson)
        File(exportRoot, "${label}_dumpsys_before.txt").writeText(dumpsysBefore)
        File(exportRoot, "${label}_dumpsys_after.txt").writeText(dumpsysAfter)
        File(exportRoot, "${label}_deviceidle_state.txt").writeText(devicePowerState)
        android.util.Log.i("PROMPT125_${label.uppercase()}_TRACE", traceJson)
    }

    fun traceJson(context: Context, result: AlarmExperimentResult): String {
        val array = JSONArray()
        TargetedTraceTestSupport.readEvents(context)
            .filter { it.name in traceEventNames }
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
            put("experiment", result.label)
            put("scheduler_api", result.schedulerApi)
            put("occurrence_id", result.occurrenceId)
            put("events", array)
            put("result", JSONObject().apply {
                put("classification", result.classification)
                put("requested_trigger_at_epoch_ms", result.requestedTriggerEpochMs)
                put("actual_alarm_fired_at_epoch_ms", result.actualAlarmFiredAtEpochMs ?: JSONObject.NULL)
                put("delay_ms", result.delayMs ?: JSONObject.NULL)
                put("system_pending_before_trigger", result.systemPendingBeforeTrigger)
                put("alarm_fired", result.alarmFired)
                put("notification_posted", result.notificationPosted)
                put("notification_visible", result.notificationVisible)
            })
        }.toString(2)
    }

    fun comparisonJson(resultA: AlarmExperimentResult, resultB: AlarmExperimentResult): String {
        return JSONObject().apply {
            put("setWindow", rowJson(resultA))
            put("setAndAllowWhileIdle", rowJson(resultB))
            put("interpretation", interpret(resultA, resultB))
        }.toString(2)
    }

    private fun rowJson(result: AlarmExperimentResult): JSONObject {
        return JSONObject().apply {
            put("scheduled", result.alarmScheduled)
            put("system_pending", result.systemPendingBeforeTrigger)
            put("fired", result.alarmFired)
            put("delay_ms", result.delayMs ?: JSONObject.NULL)
            put("receiver", result.alarmFired)
            put("notify", result.notificationPosted)
            put("visible", result.notificationVisible)
            put("classification", result.classification)
            put("requested_trigger", result.requestedTriggerEpochMs)
            put("actual_fire", result.actualAlarmFiredAtEpochMs ?: JSONObject.NULL)
        }
    }

    private fun interpret(a: AlarmExperimentResult, b: AlarmExperimentResult): String {
        return when {
            !a.alarmFired && b.alarmFired -> "CASE_1_STANDARD_INEXACT_ALARM_DEFERRED_IN_BACKGROUND"
            a.alarmFired && b.alarmFired -> "CASE_2_BOTH_FIRE_HARNESS_OR_CONDITIONS"
            !a.alarmFired && !b.alarmFired -> "CASE_3_BOTH_NO_FIRE_SYSTEM_RESTRICTION"
            !a.systemPendingBeforeTrigger -> "CASE_4_SETWINDOW_NOT_IN_DUMPSYS"
            a.systemPendingBeforeTrigger && !a.alarmFired -> "CASE_5_ALARM_PRESENT_THEN_LIFECYCLE_ISSUE"
            else -> "OTHER"
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
