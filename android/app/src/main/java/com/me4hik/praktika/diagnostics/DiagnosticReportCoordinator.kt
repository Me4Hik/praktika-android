// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import android.content.Context
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.runtime.PraktikaRuntime
import io.sentry.Sentry
import java.io.File
import org.json.JSONObject

enum class BugReportSendResult {
    REMOTE_SENT,
    SAVED_LOCALLY_NO_DSN,
    SAVED_LOCALLY_SEND_FAILED,
}

class DiagnosticReportCoordinator(
    context: Context,
    private val runtimeProvider: () -> com.me4hik.praktika.runtime.PraktikaRuntime,
) : DiagnosticReportSubmitter {
    private val identityStore = DiagnosticIdentityStore(context.applicationContext)
    private val reportStore = DiagnosticReportStore(
        File(context.filesDir, "diagnostics/reports"),
    )
    private val snapshotBuilder = DiagnosticSnapshotBuilder(
        context = context.applicationContext,
        identityStore = identityStore,
        reportStore = reportStore,
    )

    override suspend fun submitManualReport(
        testerComment: String?,
    ): BugReportSendResult {
        val runtime = runtimeProvider()
        val recorder = DiagnosticsRecorder.get()
        recorder.record(
            category = DiagnosticCategory.USER,
            name = "bug_report_submit",
            metadata = mapOf(
                "screen" to (NavigationRouteTracker.currentRoute ?: "unknown"),
                "has_comment" to (!testerComment.isNullOrBlank()).toString(),
            ),
        )
        val snapshot = snapshotBuilder.build(
            runtime = runtime,
            recorder = recorder,
            testerComment = testerComment,
        )
        snapshotBuilder.saveLocally(snapshot)
        return sendToSentryIfConfigured(snapshot)
    }

    private fun sendToSentryIfConfigured(snapshot: JSONObject): BugReportSendResult {
        if (BuildConfig.SENTRY_DSN.isBlank()) {
            return BugReportSendResult.SAVED_LOCALLY_NO_DSN
        }
        return try {
            val event = ManualBugReportSentryEventFactory.build(snapshot)
            Sentry.captureEvent(event)
            BugReportSendResult.REMOTE_SENT
        } catch (_: Exception) {
            BugReportSendResult.SAVED_LOCALLY_SEND_FAILED
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
