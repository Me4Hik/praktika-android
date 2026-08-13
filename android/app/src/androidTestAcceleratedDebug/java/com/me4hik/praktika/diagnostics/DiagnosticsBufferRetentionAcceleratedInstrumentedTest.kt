// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class DiagnosticsBufferRetentionAcceleratedInstrumentedTest {
    private val devicePreflightRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
                base.evaluate()
            }
        }
    }

    private val legacySeedRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                resetAcceleratedSandbox(context)
                seedLegacyPollutedBuffer(context)
                DiagnosticsRecorder.resetForTests()
                DiagnosticsRecorder.initialize(context.applicationContext)
                DiagnosticsRecorder.get().recordSync(
                    category = DiagnosticCategory.APP,
                    name = "process_start",
                    metadata = mapOf(
                        "version_code" to BuildConfig.VERSION_CODE.toString(),
                        "version_name" to BuildConfig.VERSION_NAME,
                        "flavor" to BuildConfig.FLAVOR,
                        "package" to context.packageName,
                    ),
                )
                base.evaluate()
            }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(devicePreflightRule)
        .around(legacySeedRule)
        .around(composeRule)

    @Test
    fun versionBoundaryCleanup_producesUsefulMixedDiagnosticReport() {
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { PraktikaRuntimeHolder.get(context).cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_SETTINGS)
        }

        openSettings()
        openBugReportDialog()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_COMMENT)
            .performTextInput("VC6 diagnostics buffer retention proof")
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_SEND).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_BUG_REPORT_RESULT)
        }
        composeRule.waitForIdle()

        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue(eventsFile.exists())
        val events = eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
        assertTrue(events.any { it.name == "diagnostics_version_boundary" })
        assertTrue(events.any { it.category == DiagnosticCategory.APP && it.name == "process_start" })
        assertTrue(events.any { it.category == DiagnosticCategory.PERMISSION })
        assertTrue(events.any { it.category == DiagnosticCategory.USER })

        val notificationCount = events.count { it.category == DiagnosticCategory.NOTIFICATION }
        assertTrue(
            "legacy NOTIFICATION should not dominate buffer after version boundary",
            notificationCount < events.size / 2,
        )

        val reportFile = File(context.filesDir, "diagnostics/reports").listFiles()?.maxByOrNull { it.lastModified() }
        checkNotNull(reportFile) { "local diagnostic report missing" }
        val snapshot = JSONObject(reportFile.readText())
        val recentEvents = snapshot.getJSONArray("recentEvents")
        assertTrue(recentEvents.length() > 0)
        var hasPermission = false
        var hasUser = false
        var hasApp = false
        var notificationInReport = 0
        for (index in 0 until recentEvents.length()) {
            val item = recentEvents.getJSONObject(index)
            when (item.getString("category")) {
                "PERMISSION" -> hasPermission = true
                "USER" -> hasUser = true
                "APP" -> hasApp = true
                "NOTIFICATION" -> notificationInReport++
            }
        }
        assertTrue(hasPermission)
        assertTrue(hasUser)
        assertTrue(hasApp)
        assertTrue(notificationInReport < recentEvents.length())
    }

    private fun seedLegacyPollutedBuffer(context: android.content.Context) {
        val diagnosticsDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "DISABLED",
            "cancel_notification" to "true",
            "show_notification" to "false",
            "alarm_count" to "0",
        )
        var seq = 0L
        fun buildEvent(
            category: DiagnosticCategory,
            name: String,
            metadata: Map<String, String> = emptyMap(),
        ): DiagnosticEvent {
            seq += 1
            return DiagnosticEvent(
                seq = seq,
                tsEpochMs = seq,
                monoMs = seq,
                category = category,
                name = name,
                metadata = metadata,
            )
        }
        val legacy = buildList {
            repeat(350) {
                add(buildEvent(DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            repeat(4) { add(buildEvent(DiagnosticCategory.PERMISSION, "notification_permission_state")) }
            repeat(3) { add(buildEvent(DiagnosticCategory.USER, "button_tap")) }
            add(buildEvent(DiagnosticCategory.ERROR, "uncaught_exception", mapOf("message" to "legacy")))
        }
        val eventsFile = File(diagnosticsDir, "events.jsonl")
        eventsFile.writeText(legacy.joinToString("\n") { it.toJsonLine() })
        File(diagnosticsDir, "recorded_version_code.txt").writeText("4")
    }

    private fun openSettings() {
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    private fun openBugReportDialog() {
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_COMMENT).assertIsDisplayed()
    }

    private fun resetAcceleratedSandbox(context: android.content.Context) {
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
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
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
