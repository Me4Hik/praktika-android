// 10.08.2026 Post-release fixes cursor by Me4Hik START - first-class Sentry manual bug report
package com.me4hik.praktika.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualBugReportSentryEventFactoryTest {
    @Test
    fun buildMessageIncludesTesterComment() {
        val message = ManualBugReportSentryEventFactory.buildMessage(
            "ТЕСТ: не работает включение уведомлений",
        )
        assertEquals(
            "Manual bug report: ТЕСТ: не работает включение уведомлений",
            message,
        )
    }

    @Test
    fun buildMessageWithoutCommentUsesDefaultPrefix() {
        assertEquals(
            "Manual bug report",
            ManualBugReportSentryEventFactory.buildMessage(""),
        )
    }

    @Test
    fun buildEventExposesBugReportContextBeforeSnapshotTail() {
        val snapshot = JSONObject()
            .put("versionCode", 2)
            .put("versionName", "1.0-accelerated")
            .put("packageName", "com.me4hik.praktika.accelerated")
            .put("runtimeMode", "accelerated")
            .put("environment", "accelerated")
            .put("deviceManufacturer", "Samsung")
            .put("deviceModel", "SM-T220")
            .put("androidRelease", "14")
            .put("androidSdk", 34)
            .put("timezone", "Europe/Kiev")
            .put("installId", "install-123")
            .put("currentRoute", "settings")
            .put("notificationPermissionGranted", false)
            .put("appNotificationsEnabled", false)
            .put("permissionRequestedFlag", true)
            .put("permissionUiState", "APP_NOTIFICATIONS_DISABLED")
            .put("practiceStarted", true)
            .put("practicePaused", false)
            .put("currentCycleNumber", 1)
            .put("occurrenceStatus", "AVAILABLE")
            .put("windowWidthPx", 1200)
            .put("windowHeightPx", 1920)
            .put("screenWidthDp", 600)
            .put("screenHeightDp", 960)
            .put("smallestScreenWidthDp", 600)
            .put("density", 2.0)
            .put("densityDpi", 320)
            .put("fontScale", 1.0)
            .put("orientation", "portrait")
            .put("insetStatusBarsDp", 24)
            .put("insetNavigationBarsDp", 48)
            .put("insetTappableElementDp", 48)
            .put("insetSystemGesturesDp", 24)
            .put("questionWidthBucket", "Medium")
            .put("testerComment", "ТЕСТ: не работает включение уведомлений")
            .put("recentEvents", org.json.JSONArray())

        val event = ManualBugReportSentryEventFactory.build(snapshot)

        assertEquals(
            "Manual bug report: ТЕСТ: не работает включение уведомлений",
            event.message?.message,
        )
        val bugReport = event.contexts["bug_report"] as Map<*, *>
        assertEquals("ТЕСТ: не работает включение уведомлений", bugReport["tester_comment"])
        assertEquals("settings", bugReport["current_route"])
        assertEquals("APP_NOTIFICATIONS_DISABLED", bugReport["permission_ui_state"])
        assertEquals("AVAILABLE", bugReport["occurrence_status"])
        assertEquals("install-123", event.getTag("install_id"))
        assertEquals("2", event.getTag("version_code"))
        assertEquals("manual_bug_report", event.getTag("report_type"))

        val summary = event.contexts["diagnostic_summary"] as Map<*, *>
        assertEquals("Samsung", summary["device_manufacturer"])
        assertEquals("14", summary["android_release"])
        assertEquals("Medium", summary["question_width_bucket"])

        val deviceWindow = event.contexts["device_window"] as Map<*, *>
        assertEquals("Samsung", deviceWindow["device_manufacturer"])
        assertEquals("14", deviceWindow["android_release"])
        assertEquals(1200, deviceWindow["window_width_px"])
        assertEquals(1920, deviceWindow["window_height_px"])
        assertEquals(600, deviceWindow["screen_width_dp"])
        assertEquals(960, deviceWindow["screen_height_dp"])
        assertEquals(600, deviceWindow["smallest_screen_width_dp"])
        assertEquals(2.0, deviceWindow["density"])
        assertEquals(320, deviceWindow["density_dpi"])
        assertEquals(1.0, deviceWindow["font_scale"])
        assertEquals("portrait", deviceWindow["orientation"])
        assertEquals(24, deviceWindow["inset_status_bars_dp"])
        assertEquals(48, deviceWindow["inset_navigation_bars_dp"])
        assertEquals(48, deviceWindow["inset_tappable_element_dp"])
        assertEquals(24, deviceWindow["inset_system_gestures_dp"])
        assertEquals("Medium", deviceWindow["question_width_bucket"])

        assertFalse(deviceWindow.containsKey("serial"))
        assertFalse(deviceWindow.containsKey("android_id"))
        assertFalse(deviceWindow.containsKey("advertising_id"))
        assertFalse(snapshot.has("serial"))
        assertFalse(snapshot.has("androidId"))
    }

    @Test
    fun buildEventWithoutOptionalWindowMetricsStillSucceeds() {
        val snapshot = JSONObject()
            .put("versionCode", 18)
            .put("versionName", "1.0")
            .put("packageName", "com.me4hik.praktika")
            .put("runtimeMode", "production")
            .put("environment", "production")
            .put("deviceModel", "Pixel 6")
            .put("androidSdk", 34)
            .put("timezone", "UTC")
            .put("installId", "install-456")
            .put("currentRoute", "question/1")
            .put("permissionUiState", "GRANTED")
            .put("notificationPermissionGranted", true)
            .put("appNotificationsEnabled", true)
            .put("permissionRequestedFlag", true)
            .put("practiceStarted", true)
            .put("practicePaused", false)
            .put("currentCycleNumber", 1)
            .put("occurrenceStatus", "AVAILABLE")
            .put("orientation", "undefined")
            .put("recentEvents", org.json.JSONArray())

        val event = ManualBugReportSentryEventFactory.build(snapshot)
        assertEquals("Manual bug report", event.message?.message)
        assertTrue(event.contexts.containsKey("device_window"))
        val deviceWindow = event.contexts["device_window"] as Map<*, *>
        assertEquals("undefined", deviceWindow["orientation"])
        assertFalse(deviceWindow.containsKey("window_width_px"))
        assertFalse(deviceWindow.containsKey("inset_navigation_bars_dp"))
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
