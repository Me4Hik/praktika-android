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
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class DiagnosticsRemoteBugReportAcceleratedInstrumentedTest {
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
    fun remoteManualBugReportSendsToSentryWhenDsnConfigured() {
        assumeTrue("SENTRY_DSN must be configured for remote proof", BuildConfig.SENTRY_DSN.isNotBlank())

        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { com.me4hik.praktika.runtime.PraktikaRuntimeHolder.get(context).cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_SETTINGS)
        }

        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_COMMENT)
            .performTextInput("ТЕСТ: не работает включение уведомлений")
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_SEND).performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_BUG_REPORT_RESULT)
        }
        composeRule.onNodeWithText("Отчёт отправлен. Спасибо!").assertIsDisplayed()

        val reportsDir = File(context.filesDir, "diagnostics/reports")
        val reportFiles = reportsDir.listFiles()?.filter { it.name.startsWith("report-") }.orEmpty()
        assertTrue("local report file should exist", reportFiles.isNotEmpty())

        val snapshot = JSONObject(reportFiles.maxBy { it.lastModified() }.readText())
        assertEquals("accelerated", snapshot.optString("environment"))
        assertEquals("accelerated", snapshot.optString("runtimeMode"))
        assertFalse(snapshot.has("question_text"))
        assertFalse(snapshot.has("answer_text"))
        assertTrue(snapshot.optString("testerComment").contains("не работает включение уведомлений"))

        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue(eventsFile.exists())
        val eventNames = eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it)?.name }
        assertTrue(eventNames.contains("bug_report_submit"))

        android.util.Log.i(TAG, "DIAG_REMOTE_REPORT_GREEN environment=accelerated report_type=manual_bug_report")
    }

    private companion object {
        const val TAG = "DiagnosticsRemoteProof"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
