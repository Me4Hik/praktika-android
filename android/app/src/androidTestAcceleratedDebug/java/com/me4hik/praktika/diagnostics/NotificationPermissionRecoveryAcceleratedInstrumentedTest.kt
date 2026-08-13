// 10.08.2026 Post-release fixes cursor by Me4Hik START - permission recovery accelerated proof
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.Stage12BlackviewE2ESupport
import com.me4hik.praktika.Stage12FinalAcceptanceSupport
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class NotificationPermissionRecoveryAcceleratedInstrumentedTest {
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
    fun denyThenRecoverViaAppSettingsPathWithoutDeadEnd() {
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            PraktikaRuntimeHolder.get(context).notificationPermissionRepository
                .notifyPermissionStateChanged(source = "test_setup")
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        runBlocking {
            PraktikaRuntimeHolder.get(context).cycleRepository.startPractice()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_NOTIFICATION_CARD)
        }

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()

        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_ACTION).performClick()
        composeRule.waitForIdle()
        Stage12FinalAcceptanceSupport.tapDenyNotificationPermissionDialog()
        Thread.sleep(2_000)
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(
                composeRule,
                PracticeTestTags.HOME_NOTIFICATION_CARD,
            )
        }

        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val permissionRepository = runtime.notificationPermissionRepository
        val soundEnabled = runBlocking { runtime.soundPreferenceRepository.soundEnabled.first() }
        val requested = runBlocking { permissionRepository.permissionRequested.first() }
        val uiState = permissionRepository.evaluateUiState(
            permissionRequested = requested,
            soundEnabled = soundEnabled,
        )
        assertTrue(requested)
        assertNotEquals(NotificationPermissionUiState.NOT_REQUESTED, uiState)

        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue(eventsFile.exists())
        val events = eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
        val eventNames = events.map { it.name }
        assertTrue(eventNames.contains("notification_permission_cta"))
        assertTrue(eventNames.contains("permission_request_started"))
        assertTrue(eventNames.contains("permission_result"))
        assertTrue(eventNames.contains("notification_permission_state"))

        val lastState = events.lastOrNull { it.name == "notification_permission_state" }
        checkNotNull(lastState)
        assertTrue(lastState.metadata.containsKey("resolved_ui_state"))
        assertTrue(lastState.metadata.containsKey("runtime_granted"))
        assertTrue(lastState.metadata.containsKey("should_show_rationale"))
        assertTrue(lastState.metadata.containsKey("app_notifications_enabled"))
        assertTrue(lastState.metadata.containsKey("selected_channel_enabled"))

        val recoveryStates = setOf(
            NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED.name,
            NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED.name,
        )
        assertTrue(
            "Expected recovery state after deny, got ${lastState.metadata["resolved_ui_state"]}",
            lastState.metadata["resolved_ui_state"] in recoveryStates,
        )
        assertEquals("false", lastState.metadata["runtime_granted"])

        val recoveryAction = device.findObject(By.text("Включить"))
            ?: device.findObject(By.text("Разрешить уведомления"))
        assertTrue(
            "Recovery CTA should remain visible after deny",
            recoveryAction != null ||
                PracticeComposeTestSupport.hasNodeWithTag(
                    composeRule,
                    PracticeTestTags.HOME_NOTIFICATION_ACTION,
                ),
        )
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
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
