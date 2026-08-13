// 10.08.2026 Post-release fixes cursor by Me4Hik START - permission UI refresh without restart
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class NotificationPermissionUiRefreshAcceleratedInstrumentedTest {
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
    fun z_runtimeAllow_hidesHomeNotificationCardWithoutRestart() {
        launchHomeWithNotificationCard()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_ACTION).performClick()
        composeRule.waitForIdle()
        Stage12BlackviewE2ESupport.tapAllowNotificationPermissionDialog()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            !PracticeComposeTestSupport.hasNodeWithTag(
                composeRule,
                PracticeTestTags.HOME_NOTIFICATION_CARD,
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val soundEnabled = runBlocking { runtime.soundPreferenceRepository.soundEnabled.first() }
        val requested = runBlocking {
            runtime.notificationPermissionRepository.permissionRequested.first()
        }
        val uiState = runtime.notificationPermissionRepository.evaluateUiState(
            permissionRequested = requested,
            soundEnabled = soundEnabled,
        )
        assertEquals(NotificationPermissionUiState.ENABLED, uiState)

        val events = readDiagnosticEvents(context)
        assertTrue(events.any { it.name == "permission_ui_refresh_trigger" })
        assertTrue(events.any { it.name == "permission_result" })
        val lastState = events.lastOrNull { it.name == "notification_permission_state" }
        checkNotNull(lastState)
        assertEquals(NotificationPermissionUiState.ENABLED.name, lastState.metadata["resolved_ui_state"])
        assertEquals("true", lastState.metadata["runtime_granted"])
        assertEquals("true", lastState.metadata["app_notifications_enabled"])
    }

    @Test
    fun a_runtimeDeny_updatesHomeRecoveryCardWithoutRestart() {
        launchHomeWithNotificationCard()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_ACTION).performClick()
        composeRule.waitForIdle()
        Stage12FinalAcceptanceSupport.tapDenyNotificationPermissionDialog()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(
                composeRule,
                PracticeTestTags.HOME_NOTIFICATION_CARD,
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        val soundEnabled = runBlocking { runtime.soundPreferenceRepository.soundEnabled.first() }
        val requested = runBlocking {
            runtime.notificationPermissionRepository.permissionRequested.first()
        }
        val uiState = runtime.notificationPermissionRepository.evaluateUiState(
            permissionRequested = requested,
            soundEnabled = soundEnabled,
        )
        assertTrue(requested)
        assertNotEquals(NotificationPermissionUiState.NOT_REQUESTED, uiState)

        val events = readDiagnosticEvents(context)
        assertTrue(events.any { it.name == "permission_ui_refresh_trigger" })
        val refresh = events.last { it.name == "permission_ui_refresh_trigger" }
        assertEquals("runtime_permission_callback", refresh.metadata["source"])
    }

    private fun launchHomeWithNotificationCard() {
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
        runBlocking { PraktikaRuntimeHolder.get(context).cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_NOTIFICATION_CARD)
        }
    }

    private fun readDiagnosticEvents(context: android.content.Context): List<DiagnosticEvent> {
        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue(eventsFile.exists())
        return eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
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
