// 10.08.2026 Post-release fixes cursor by Me4Hik START - permission UI refresh without restart
package com.me4hik.praktika.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 15 kills the in-process host when POST_NOTIFICATIONS is granted via shell
 * while the app process is alive. Use two instrumentation phases:
 *
 * Phase 1: prove NOT_REQUESTED, background activity, persist marker.
 * Shell (outside instrumentation): pm grant <pkg> android.permission.POST_NOTIFICATIONS
 * Phase 2: fresh process, resume activity, verify lifecycle refresh.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionLifecycleRefreshAcceleratedInstrumentedTest {
    @Test
    fun phase1_backgroundBeforeExternalGrant() {
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
        AcceleratedDeviceTestHarness.resetSandboxFull()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(hasPostNotifications(context))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            runBlocking { MainActivityInitGate.awaitInit() }
            scenario.moveToState(Lifecycle.State.RESUMED)

            val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
            runBlocking { runtime.cycleRepository.startPractice() }

            val soundEnabled = runBlocking { runtime.soundPreferenceRepository.soundEnabled.first() }
            val requestedBefore = runBlocking {
                runtime.notificationPermissionRepository.permissionRequested.first()
            }
            val uiStateBefore = runtime.notificationPermissionRepository.evaluateUiState(
                permissionRequested = requestedBefore,
                soundEnabled = soundEnabled,
            )
            assertEquals(NotificationPermissionUiState.NOT_REQUESTED, uiStateBefore)

            scenario.moveToState(Lifecycle.State.STARTED)
            AcceleratedDeviceTestHarness.exportPrompt131(
                "permission_refresh_phase1",
                JSONObject().apply {
                    put("ui_state_before", uiStateBefore.name)
                    put("permission_requested", requestedBefore)
                    put("runtime_granted", hasPostNotifications(context))
                    put("device_slug", AcceleratedDeviceTestHarness.deviceArtifactSlug())
                },
            )
        }
    }

    @Test
    fun phase2_resumeAfterExternalGrant() {
        PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
        AcceleratedDeviceTestHarness.resetRuntimeOnly()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "Preflight: pm grant POST_NOTIFICATIONS before this instrumentation run",
            hasPostNotifications(context),
        )
        AcceleratedDeviceTestHarness.readPrompt131Json("permission_refresh_phase1.json")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            runBlocking { MainActivityInitGate.awaitInit() }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(500)

            val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
            val soundEnabled = runBlocking { runtime.soundPreferenceRepository.soundEnabled.first() }
            val requestedAfter = runBlocking {
                runtime.notificationPermissionRepository.permissionRequested.first()
            }
            val uiStateAfter = runtime.notificationPermissionRepository.evaluateUiState(
                permissionRequested = requestedAfter,
                soundEnabled = soundEnabled,
            )
            assertEquals(NotificationPermissionUiState.ENABLED, uiStateAfter)

            TargetedTraceTestSupport.awaitDiagnosticsQuiescence(context)
            val events = readDiagnosticEvents(context)
            assertTrue(
                events.any {
                    it.name == "permission_ui_refresh_trigger" &&
                        it.metadata["source"] == "activity_resume"
                },
            )
            AcceleratedDeviceTestHarness.exportPrompt131(
                "permission_refresh_phase2",
                JSONObject().apply {
                    put("ui_state_after", uiStateAfter.name)
                    put("runtime_granted", hasPostNotifications(context))
                },
            )
        }
    }

    private fun hasPostNotifications(context: android.content.Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun readDiagnosticEvents(context: android.content.Context): List<DiagnosticEvent> {
        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        assertTrue(eventsFile.exists())
        return eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
