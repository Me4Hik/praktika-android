// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt125 AlarmManager A/B on Blackview
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.notification.AlarmSchedulerApiMode
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import java.io.File
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AlarmManagerAbExperimentAcceleratedInstrumentedTest {
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
                AlarmAbExperimentSupport.resetSandbox()
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

    @Test(timeout = 900_000)
    fun experimentA_setWindowBaseline() {
        val result = AlarmAbExperimentSupport.runExperiment(
            composeRule = composeRule,
            label = "alarm_setwindow",
            apiMode = AlarmSchedulerApiMode.SET_WINDOW,
            expectedSchedulerApi = "setWindow",
        )
        assertEquals("setWindow", result.schedulerApi)
        assertFalse(AlarmAbExperimentSupport.FORCE_STOP_AFTER_SCHEDULE)
        android.util.Log.i(
            AlarmAbExperimentSupport.TAG,
            "A_setWindow classification=${result.classification} fired=${result.alarmFired}",
        )
        lastResultA = result
    }

    @Test(timeout = 900_000)
    fun experimentB_setAndAllowWhileIdle() {
        val result = AlarmAbExperimentSupport.runExperiment(
            composeRule = composeRule,
            label = "alarm_allowwhileidle",
            apiMode = AlarmSchedulerApiMode.SET_AND_ALLOW_WHILE_IDLE,
            expectedSchedulerApi = "setAndAllowWhileIdle",
        )
        assertEquals("setAndAllowWhileIdle", result.schedulerApi)
        android.util.Log.i(
            AlarmAbExperimentSupport.TAG,
            "B_setAndAllowWhileIdle classification=${result.classification} fired=${result.alarmFired}",
        )
        lastResultB = result
        if (lastResultA != null) {
            writeComparisonArtifacts(lastResultA!!, result)
        }
    }

    private fun writeComparisonArtifacts(a: AlarmExperimentResult, b: AlarmExperimentResult) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exportRoot = File(context.getExternalFilesDir(null), "prompt125")
        exportRoot.mkdirs()
        File(exportRoot, "alarm_ab_comparison.json").writeText(
            AlarmAbExperimentSupport.comparisonJson(a, b),
        )
        File(exportRoot, "alarm_timing.json").writeText(
            buildTimingJson(a, b),
        )
        File(exportRoot, "test_results.txt").writeText(
            buildTestResults(a, b),
        )
    }

    private fun buildTimingJson(a: AlarmExperimentResult, b: AlarmExperimentResult): String {
        return org.json.JSONObject().apply {
            put("setWindow", timingRow(a))
            put("setAndAllowWhileIdle", timingRow(b))
        }.toString(2)
    }

    private fun timingRow(r: AlarmExperimentResult): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("requested_trigger_at_epoch_ms", r.requestedTriggerEpochMs)
            put("actual_alarm_fired_at_epoch_ms", r.actualAlarmFiredAtEpochMs ?: org.json.JSONObject.NULL)
            put("delay_ms", r.delayMs ?: org.json.JSONObject.NULL)
            put("schedule_changed_at_epoch_ms", r.scheduleChangedAtEpochMs ?: org.json.JSONObject.NULL)
            put("app_backgrounded_at_epoch_ms", r.appBackgroundedAtEpochMs ?: org.json.JSONObject.NULL)
            put("window_ms", r.windowMs)
        }
    }

    private fun buildTestResults(a: AlarmExperimentResult, b: AlarmExperimentResult): String {
        return buildString {
            appendLine("PROMPT125 AlarmManager A/B Blackview")
            appendLine("BACKGROUND_METHOD_USED=${AlarmAbExperimentSupport.BACKGROUND_METHOD}")
            appendLine("FORCE_STOP_AFTER_SCHEDULE=${AlarmAbExperimentSupport.FORCE_STOP_AFTER_SCHEDULE}")
            appendLine()
            appendLine("A setWindow: ${a.classification} fired=${a.alarmFired} pending=${a.systemPendingBeforeTrigger}")
            appendLine("B setAndAllowWhileIdle: ${b.classification} fired=${b.alarmFired} pending=${b.systemPendingBeforeTrigger}")
            appendLine()
            appendLine("interpretation=${AlarmAbExperimentSupport.comparisonJson(a, b).let { org.json.JSONObject(it).getString("interpretation") }}")
        }
    }

    companion object {
        @Volatile
        var lastResultA: AlarmExperimentResult? = null

        @Volatile
        var lastResultB: AlarmExperimentResult? = null

        @AfterClass
        @JvmStatic
        fun afterClass() {
            val a = lastResultA
            val b = lastResultB
            if (a != null && b != null) {
                android.util.Log.i(
                    AlarmAbExperimentSupport.TAG,
                    "FINAL_A_B a_fired=${a.alarmFired} b_fired=${b.alarmFired}",
                )
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
