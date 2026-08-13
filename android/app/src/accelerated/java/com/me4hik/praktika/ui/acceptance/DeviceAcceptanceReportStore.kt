// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - private app report store
package com.me4hik.praktika.ui.acceptance

import android.content.Context
import java.io.File

class DeviceAcceptanceReportStore(
    private val context: Context,
) {
    fun writeLatest(report: DeviceAcceptanceEvidenceReport): String {
        val dir = File(context.filesDir, DeviceAcceptanceReportSerializer.REPORT_DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, DeviceAcceptanceReportSerializer.LATEST_REPORT_FILE_NAME)
        val json = DeviceAcceptanceReportSerializer.toJson(report)
        file.writeText(json, Charsets.UTF_8)
        return DeviceAcceptanceReportSerializer.LATEST_REPORT_FILE_NAME
    }

    fun relativeDirectory(): String = DeviceAcceptanceReportSerializer.REPORT_DIRECTORY_NAME
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
