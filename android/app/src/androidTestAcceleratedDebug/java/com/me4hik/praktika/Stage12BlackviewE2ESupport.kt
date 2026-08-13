// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - Stage 12 E2E helpers
package com.me4hik.praktika

import android.Manifest
import android.app.UiAutomation
import android.os.Build
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.notification.PracticeNotificationChannels
import com.me4hik.praktika.runtime.PraktikaRuntime
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

object Stage12BlackviewE2ESupport {
    // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik START - opt-in gate for ordinary connected
    private const val MILESTONE_ARGUMENT = "stage12_notification_e2e"

    fun isMilestoneOptIn(): Boolean {
        return InstrumentationRegistry.getArguments()
            .getString(MILESTONE_ARGUMENT)
            .toBoolean()
    }

    fun assumeMilestoneOptIn() {
        assumeTrue(
            "Stage 12 notification milestone runs only through scripts/stage12_blackview_notification_e2e.ps1",
            isMilestoneOptIn(),
        )
    }
    // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik END

    fun grantPostNotificationsViaShell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                pkg,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant $pkg android.permission.POST_NOTIFICATIONS",
            ).close()
        }
    }

    /**
     * Do not call from instrumentation on Android 15 — pm revoke kills the host process.
     * Use host adb between separate instrumentation invocations instead.
     */
    fun revokePostNotificationsViaShell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "pm revoke $pkg android.permission.POST_NOTIFICATIONS",
        ).close()
    }

    fun clearNotificationPermissionFlag() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteSharedPreferences("praktika_notification_permission").also {
            context.getSharedPreferences("praktika_notification_permission", 0).edit().clear().apply()
        }
    }

    fun tapAllowNotificationPermissionDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        val deadline = System.currentTimeMillis() + 15_000
        var allow = device.findObject(By.text("Allow"))
            ?: device.findObject(By.text("Разрешить"))
        while (allow == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            device.waitForIdle()
            allow = device.findObject(By.text("Allow"))
                ?: device.findObject(By.text("Разрешить"))
        }
        checkNotNull(allow) { "System Allow button not found" }
        allow.click()
        device.waitForIdle()
    }

    fun dumpsysAlarmContainsPackage(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        val pfd = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        val dump = try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
        assertTrue("Expected alarm dump to mention $pkg", dump.contains(pkg))
        return dump
    }

    suspend fun assertQuickCheckOk(runtime: PraktikaRuntime) {
        Stage11BlackviewE2ESupport.assertQuickCheckOk(runtime)
    }

    fun ensureChannels(runtime: PraktikaRuntime) {
        kotlinx.coroutines.runBlocking {
            runtime.notificationCoordinator.sync(com.me4hik.praktika.notification.NotificationSyncReason.APP_START)
        }
    }

    fun resetAcceleratedSandboxViaShell() {
        revokePostNotificationsViaShell()
        clearNotificationPermissionFlag()
    }

    // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik START - scenario background (KEYCODE_HOME breaks ActivityScenario binding)
    fun backgroundAppForAlarmDelivery(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ) {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(1_000)
    }

    fun bringMainActivityToForeground(
        composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ) {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }
    // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik END

    fun channelIds(): Pair<String, String> {
        return PracticeNotificationChannels.SOUND to PracticeNotificationChannels.SILENT
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
