// 10.08.2026 Post-release fixes cursor by Me4Hik START - targeted real-bug trace test helpers
package com.me4hik.praktika.diagnostics

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object TargetedTraceTestSupport {
    fun readEvents(context: Context): List<DiagnosticEvent> {
        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        if (!eventsFile.exists()) {
            return emptyList()
        }
        return eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
    }

    fun eventNames(context: Context): List<String> = readEvents(context).map { it.name }

    fun lastEvent(context: Context, name: String): DiagnosticEvent? {
        return readEvents(context).lastOrNull { it.name == name }
    }

    fun eventsAfter(context: Context, name: String, afterSeq: Long): List<DiagnosticEvent> {
        return readEvents(context).filter { it.seq > afterSeq && it.name == name }
    }

    fun waitForEventAfter(
        context: Context,
        name: String,
        afterSeq: Long,
        timeoutMillis: Long = 10_000,
        predicate: (DiagnosticEvent) -> Boolean = { true },
    ): DiagnosticEvent? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: DiagnosticEvent? = null
        while (System.currentTimeMillis() < deadline) {
            awaitDiagnosticsQuiescence(context, stablePolls = 2)
            last = eventsAfter(context, name, afterSeq).lastOrNull(predicate)
            if (last != null) {
                return last
            }
            Thread.sleep(200)
        }
        return last
    }

    /**
     * DiagnosticEventStore writes asynchronously; wait until events.jsonl size stabilizes
     * so harness reads do not race the IO channel flush.
     */
    fun awaitDiagnosticsQuiescence(
        context: Context,
        timeoutMillis: Long = 5_000,
        stablePolls: Int = 3,
    ) {
        val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
        val deadline = System.currentTimeMillis() + timeoutMillis
        var stableCount = 0
        var lastSize = -1L
        while (System.currentTimeMillis() < deadline && stableCount < stablePolls) {
            val size = if (eventsFile.exists()) eventsFile.length() else 0L
            if (size == lastSize) {
                stableCount++
            } else {
                stableCount = 0
                lastSize = size
            }
            Thread.sleep(150)
        }
    }

    fun describeRecentEvents(context: Context, limit: Int = 20): String {
        return readEvents(context).takeLast(limit).joinToString("\n") { event ->
            "seq=${event.seq} name=${event.name} meta=${event.metadata}"
        }
    }

    fun latestReport(context: Context): JSONObject? {
        val reportsDir = File(context.filesDir, "diagnostics/reports")
        val reportFiles = reportsDir.listFiles()?.filter { it.name.startsWith("report-") }.orEmpty()
        if (reportFiles.isEmpty()) {
            return null
        }
        return JSONObject(reportFiles.maxBy { it.lastModified() }.readText())
    }

    fun submitManualReport(
        composeRule: ComposeContentTestRule,
        comment: String,
    ) {
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_COMMENT)
            .performTextInput(comment)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_SEND).performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_RESULT)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BUG_REPORT_CLOSE).performClick()
        composeRule.waitForIdle()
    }

    fun reportContainsEventNames(context: Context, vararg names: String): Boolean {
        val report = latestReport(context) ?: return false
        val recent = report.optJSONArray("recentEvents") ?: return false
        val reportNames = buildList {
            for (index in 0 until recent.length()) {
                add(recent.getJSONObject(index).getString("name"))
            }
        }
        return names.all { reportNames.contains(it) }
    }

    fun exportDiagnosticsToExternal(context: Context, label: String, exportSubdir: String = "prompt124") {
        val diagnosticsDir = File(context.filesDir, "diagnostics")
        if (!diagnosticsDir.exists()) {
            return
        }
        val exportRoot = File(context.getExternalFilesDir(null), exportSubdir)
        exportRoot.mkdirs()
        diagnosticsDir.listFiles()?.forEach { file ->
            file.copyTo(File(exportRoot, "${label}_${file.name}"), overwrite = true)
        }
    }

    fun relevantEventsJson(context: Context, eventNames: Set<String>): String {
        val array = JSONArray()
        readEvents(context).filter { it.name in eventNames }.forEach { event ->
            array.put(
                JSONObject().apply {
                    put("seq", event.seq)
                    put("timestamp", event.tsEpochMs)
                    put("category", event.category.name)
                    put("event", event.name)
                    put("metadata", JSONObject(event.metadata))
                },
            )
        }
        return array.toString()
    }

    fun logPrompt124Trace(tag: String, context: Context, eventNames: Set<String>) {
        val payload = relevantEventsJson(context, eventNames)
        android.util.Log.i(tag, payload)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
