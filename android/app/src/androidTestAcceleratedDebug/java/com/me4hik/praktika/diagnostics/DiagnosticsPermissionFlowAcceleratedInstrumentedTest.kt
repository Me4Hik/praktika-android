// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.Stage12BlackviewE2ESupport
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class DiagnosticsPermissionFlowAcceleratedInstrumentedTest {
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

    @Test
    fun permissionFlowWritesExpectedDiagnosticSequence() {
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { PraktikaRuntimeHolder.get(context).cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_NOTIFICATION_CARD)
        }

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()

        val cta = device.findObject(By.text("Разрешить уведомления"))
            ?: device.findObject(By.textContains("уведомлен"))
        checkNotNull(cta) { "Notification permission CTA not visible on Home" }
        cta.click()
        device.waitForIdle()

        val allow = device.findObject(By.text("Разрешить"))
            ?: device.findObject(By.text("Allow"))
        if (allow != null) {
            allow.click()
            device.waitForIdle()
        }

        composeRule.waitForIdle()
        Thread.sleep(2_000)

        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue("events file should exist", eventsFile.exists())
        val eventNames = eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it)?.name }

        listOf(
            "notification_permission_cta",
            "permission_request_started",
            "activity_pause",
            "permission_result",
            "activity_resume",
            "notification_sync_started",
            "notification_sync_result",
            "notification_permission_state",
        ).forEach { expected ->
            assertTrue("Missing diagnostic event: $expected; got=$eventNames", eventNames.contains(expected))
        }

        android.util.Log.i(TAG, "DIAG_PERMISSION_FLOW_GREEN events=$eventNames")
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
        const val TAG = "DiagnosticsPermissionProof"
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
