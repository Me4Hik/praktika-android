// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.diagnostics.DiagnosticEvent.Companion.fromJsonLine
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class DiagnosticsBugReportAcceleratedInstrumentedTest {
    private val devicePreflightRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
                base.evaluate()
            }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(devicePreflightRule)
        .around(composeRule)

    @Test
    fun manualBugReportCreatesLocalEvidenceAndRecordsNavigationEvents() {
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { PraktikaRuntimeHolder.get(context).cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_SETTINGS)
        }

        openSettings()
        openBugReportDialog()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_CANCEL).performClick()
        composeRule.waitForIdle()

        openBugReportDialog()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_COMMENT)
            .performTextInput("diag test comment")
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_SEND).performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_BUG_REPORT_RESULT)
        }
        composeRule.onNodeWithText("Отчёт сохранён локально, отправка недоступна.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_CLOSE).performClick()
        composeRule.waitForIdle()

        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue("events file should exist", eventsFile.exists())
        val eventNames = eventsFile.readLines()
            .mapNotNull { line -> fromJsonLine(line)?.name }
        assertTrue(eventNames.contains("process_start"))
        assertTrue(eventNames.contains("activity_create"))
        assertTrue(eventNames.contains("screen_open"))
        assertTrue(eventNames.contains("bug_report_submit"))

        val reportsDir = File(context.filesDir, "diagnostics/reports")
        val reportFiles = reportsDir.listFiles()?.filter { it.name.startsWith("report-") }.orEmpty()
        assertTrue("local report file should exist", reportFiles.isNotEmpty())

        val snapshot = JSONObject(reportFiles.maxBy { it.lastModified() }.readText())
        assertFalse(snapshot.has("question_text"))
        assertFalse(snapshot.has("answer_text"))
        assertTrue(snapshot.has("recentEvents"))
        assertTrue(snapshot.optString("testerComment").contains("diag test comment"))
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
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
