// 10.08.2026 Post-release fixes cursor by Me4Hik START - first-class Sentry manual bug report
package com.me4hik.praktika.diagnostics

import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import org.json.JSONObject

internal object ManualBugReportSentryEventFactory {
    private const val MESSAGE_PREFIX = "Manual bug report"
    private const val MESSAGE_COMMENT_MAX_LENGTH = 120

    fun build(snapshot: JSONObject): SentryEvent {
        val testerComment = snapshot.optString("testerComment").trim()
        return SentryEvent().apply {
            level = SentryLevel.INFO
            message = Message().apply {
                message = buildMessage(testerComment)
            }
            setTag("report_type", "manual_bug_report")
            setTag("environment", snapshot.optString("environment"))
            setTag("runtime_mode", snapshot.optString("runtimeMode"))
            setTag("version_code", snapshot.optInt("versionCode").toString())
            snapshot.optString("installId")
                .takeIf { it.isNotBlank() }
                ?.let { installId -> setTag("install_id", installId) }
            contexts["bug_report"] = buildBugReportContext(snapshot, testerComment)
            contexts["diagnostic_summary"] = buildDiagnosticSummaryContext(snapshot)
            buildDeviceWindowContext(snapshot)?.let { deviceWindow ->
                contexts["device_window"] = deviceWindow
            }
            setExtra("diagnostic_snapshot", snapshot.toString())
        }
    }

    fun buildMessage(testerComment: String): String {
        if (testerComment.isBlank()) {
            return MESSAGE_PREFIX
        }
        val truncated = testerComment.take(MESSAGE_COMMENT_MAX_LENGTH)
        val suffix = if (testerComment.length > MESSAGE_COMMENT_MAX_LENGTH) "…" else ""
        return "$MESSAGE_PREFIX: $truncated$suffix"
    }

    private fun buildBugReportContext(
        snapshot: JSONObject,
        testerComment: String,
    ): Map<String, Any> {
        return buildMap {
            if (testerComment.isNotBlank()) {
                put("tester_comment", testerComment)
            }
            put("current_route", snapshot.optString("currentRoute"))
            put("permission_ui_state", snapshot.optString("permissionUiState"))
            put("notification_permission_granted", snapshot.optBoolean("notificationPermissionGranted"))
            put("app_notifications_enabled", snapshot.optBoolean("appNotificationsEnabled"))
            put("permission_requested_flag", snapshot.optBoolean("permissionRequestedFlag"))
            put("occurrence_status", snapshot.optString("occurrenceStatus"))
            put("install_id", snapshot.optString("installId"))
        }
    }

    private fun buildDiagnosticSummaryContext(snapshot: JSONObject): Map<String, Any> {
        return buildMap {
            put("version_code", snapshot.optInt("versionCode"))
            put("version_name", snapshot.optString("versionName"))
            put("package_name", snapshot.optString("packageName"))
            if (snapshot.has("deviceManufacturer")) {
                put("device_manufacturer", snapshot.optString("deviceManufacturer"))
            }
            put("device_model", snapshot.optString("deviceModel"))
            if (snapshot.has("androidRelease")) {
                put("android_release", snapshot.optString("androidRelease"))
            }
            put("android_sdk", snapshot.optInt("androidSdk"))
            put("timezone", snapshot.optString("timezone"))
            put("runtime_mode", snapshot.optString("runtimeMode"))
            put("environment", snapshot.optString("environment"))
            put("practice_started", snapshot.optBoolean("practiceStarted"))
            put("practice_paused", snapshot.optBoolean("practicePaused"))
            put("current_cycle_number", snapshot.optInt("currentCycleNumber"))
            if (snapshot.has("occurrenceId")) {
                put("occurrence_id", snapshot.optLong("occurrenceId"))
            }
            if (snapshot.has("questionWidthBucket")) {
                put("question_width_bucket", snapshot.optString("questionWidthBucket"))
            }
        }
    }

    private fun buildDeviceWindowContext(snapshot: JSONObject): Map<String, Any>? {
        val context = buildMap {
            putIfPresentString(snapshot, "deviceManufacturer", "device_manufacturer")
            putIfPresentString(snapshot, "androidRelease", "android_release")
            putIfPresentNumber(snapshot, "windowWidthPx", "window_width_px")
            putIfPresentNumber(snapshot, "windowHeightPx", "window_height_px")
            putIfPresentNumber(snapshot, "screenWidthDp", "screen_width_dp")
            putIfPresentNumber(snapshot, "screenHeightDp", "screen_height_dp")
            putIfPresentNumber(snapshot, "smallestScreenWidthDp", "smallest_screen_width_dp")
            putIfPresentNumber(snapshot, "density", "density")
            putIfPresentNumber(snapshot, "densityDpi", "density_dpi")
            putIfPresentNumber(snapshot, "fontScale", "font_scale")
            if (snapshot.has("orientation")) {
                put("orientation", snapshot.optString("orientation"))
            }
            putIfPresentNumber(snapshot, "insetStatusBarsDp", "inset_status_bars_dp")
            putIfPresentNumber(snapshot, "insetNavigationBarsDp", "inset_navigation_bars_dp")
            putIfPresentNumber(snapshot, "insetTappableElementDp", "inset_tappable_element_dp")
            putIfPresentNumber(snapshot, "insetSystemGesturesDp", "inset_system_gestures_dp")
            putIfPresentString(snapshot, "questionWidthBucket", "question_width_bucket")
        }
        return context.takeIf { it.isNotEmpty() }
    }

    private fun MutableMap<String, Any>.putIfPresentString(
        snapshot: JSONObject,
        sourceKey: String,
        targetKey: String,
    ) {
        if (snapshot.has(sourceKey)) {
            put(targetKey, snapshot.optString(sourceKey))
        }
    }

    private fun MutableMap<String, Any>.putIfPresentNumber(
        snapshot: JSONObject,
        sourceKey: String,
        targetKey: String,
    ) {
        if (!snapshot.has(sourceKey)) {
            return
        }
        put(targetKey, snapshot.get(sourceKey))
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
