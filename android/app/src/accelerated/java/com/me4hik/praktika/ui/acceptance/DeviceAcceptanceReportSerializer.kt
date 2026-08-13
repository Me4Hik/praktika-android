// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - sanitized report JSON (no URI/answers)
package com.me4hik.praktika.ui.acceptance

import org.json.JSONArray
import org.json.JSONObject

object DeviceAcceptanceReportSerializer {
    const val LATEST_REPORT_FILE_NAME = "latest_report.json"
    const val REPORT_DIRECTORY_NAME = "device-acceptance"

    fun toJson(report: DeviceAcceptanceEvidenceReport): String {
        val root = JSONObject()
        root.put("action", report.action)
        root.put("resultStatus", report.resultStatus.name)
        root.put("harnessExecutedAtEpochMillis", report.harnessExecutedAtEpochMillis)
        root.put("slotA", report.slotA?.let { slotJson(it) } ?: JSONObject.NULL)
        root.put("slotB", report.slotB?.let { slotJson(it) } ?: JSONObject.NULL)
        root.put("latestValidSlot", report.latestValidSlot?.name ?: JSONObject.NULL)
        root.put(
            "latestValidSequence",
            report.latestValidSequence ?: JSONObject.NULL,
        )
        root.put(
            "latestValidSemantics",
            report.latestValidSemantics?.let { semanticsJson(it) } ?: JSONObject.NULL,
        )
        root.put(
            "folderFingerprintSha256",
            report.folderFingerprintSha256 ?: JSONObject.NULL,
        )
        // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - serialize roomSummary
        root.put(
            "roomSummary",
            report.roomSummary?.let { roomSummaryJson(it) } ?: JSONObject.NULL,
        )
        // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
        return root.toString(2)
    }

    fun containsForbiddenLeak(serialized: String): Boolean {
        val lower = serialized.lowercase()
        if (lower.contains("content://")) return true
        if (lower.contains("documentid")) return true
        if (lower.contains("/storage/")) return true
        if (lower.contains("tree/")) return true
        return false
    }

    private fun slotJson(slot: DeviceAcceptanceSlotSummary): JSONObject {
        return JSONObject().apply {
            put("slot", slot.slot.name)
            put("exists", slot.exists)
            put("byteSize", slot.byteSize ?: JSONObject.NULL)
            put("fullFileSha256", slot.fullFileSha256 ?: JSONObject.NULL)
            put("decodeStatus", slot.decodeStatus.name)
            put("checksumValid", slot.checksumValid ?: JSONObject.NULL)
            put("sequence", slot.sequence ?: JSONObject.NULL)
            put("createdAtEpochMillis", slot.createdAtEpochMillis ?: JSONObject.NULL)
        }
    }

    private fun semanticsJson(summary: DeviceAcceptanceSemanticSummary): JSONObject {
        return JSONObject().apply {
            put("backupSchemaVersion", summary.backupSchemaVersion)
            put("sequence", summary.sequence)
            put("createdAtEpochMillis", summary.createdAtEpochMillis)
            put("sourceVersionCode", summary.sourceVersionCode)
            put("sourceVersionName", summary.sourceVersionName)
            put("practiceStarted", summary.practiceStarted)
            put("isPaused", summary.isPaused)
            put("currentCycleNumber", summary.currentCycleNumber)
            put("nextCyclePosition", summary.nextCyclePosition)
            put("scheduleMinutes", JSONArray(summary.scheduleMinutes))
            put("occurrenceCount", summary.occurrenceCount)
            put("answerCount", summary.answerCount)
            put("deletedTextCount", summary.deletedTextCount)
            put("answeredCount", summary.answeredCount)
            put("skippedCount", summary.skippedCount)
            put("scheduledCount", summary.scheduledCount)
            put("missedCount", summary.missedCount)
        }
    }

    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - room summary JSON
    private fun roomSummaryJson(summary: DeviceAcceptanceRoomStateSummary): JSONObject {
        return JSONObject().apply {
            put("practiceStarted", summary.practiceStarted)
            put("isPaused", summary.isPaused)
            put("currentCycleNumber", summary.currentCycleNumber)
            put("nextCyclePosition", summary.nextCyclePosition)
            put("scheduleMinutes", JSONArray(summary.scheduleMinutes))
            put("occurrenceCount", summary.occurrenceCount)
            put("answerCount", summary.answerCount)
            put("deletedTextCount", summary.deletedTextCount)
            put("answeredCount", summary.answeredCount)
            put("skippedCount", summary.skippedCount)
            put("scheduledCount", summary.scheduledCount)
            put("missedCount", summary.missedCount)
            put("availableCount", summary.availableCount)
        }
    }
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
