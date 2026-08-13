// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticReportStore(
    private val reportsDir: File,
    private val maxReports: Int = MAX_REPORTS,
) {
    init {
        reportsDir.mkdirs()
    }

    fun saveReport(snapshot: JSONObject): File {
        val file = File(reportsDir, "report-${System.currentTimeMillis()}.json")
        file.writeText(snapshot.toString())
        trimOldReports()
        return file
    }

    fun listReports(): List<File> {
        return reportsDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("report-") && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun trimOldReports() {
        val reports = listReports()
        if (reports.size <= maxReports) {
            return
        }
        reports.drop(maxReports).forEach { it.delete() }
    }

    companion object {
        const val MAX_REPORTS = 10
    }
}

object DiagnosticSnapshotJson {
    fun eventsArray(events: List<DiagnosticEvent>): JSONArray {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("seq", event.seq)
                    .put("tsEpochMs", event.tsEpochMs)
                    .put("monoMs", event.monoMs)
                    .put("category", event.category.name)
                    .put("name", event.name)
                    .put("metadata", JSONObject(event.metadata)),
            )
        }
        return array
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
