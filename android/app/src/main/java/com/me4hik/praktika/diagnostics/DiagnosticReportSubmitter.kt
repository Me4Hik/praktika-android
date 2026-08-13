// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

interface DiagnosticReportSubmitter {
    suspend fun submitManualReport(testerComment: String?): BugReportSendResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
