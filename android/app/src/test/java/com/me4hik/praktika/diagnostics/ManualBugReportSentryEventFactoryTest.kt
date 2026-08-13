// 10.08.2026 Post-release fixes cursor by Me4Hik START - first-class Sentry manual bug report
package com.me4hik.praktika.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
            .put("deviceModel", "XPLORE 2")
            .put("androidSdk", 35)
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
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
