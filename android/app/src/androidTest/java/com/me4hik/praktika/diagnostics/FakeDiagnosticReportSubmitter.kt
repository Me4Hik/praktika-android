// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

class FakeDiagnosticReportSubmitter : DiagnosticReportSubmitter {
    var lastComment: String? = null
    var result: BugReportSendResult = BugReportSendResult.SAVED_LOCALLY_NO_DSN

    override suspend fun submitManualReport(testerComment: String?): BugReportSendResult {
        lastComment = testerComment
        return result
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
