// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import android.content.Context
import java.io.File

internal class DiagnosticVersionStore(context: Context) {
    private val versionFile = File(context.filesDir, "diagnostics/recorded_version_code.txt")

    fun readRecordedVersionCode(): Int? {
        if (!versionFile.exists()) {
            return null
        }
        return versionFile.readText().trim().toIntOrNull()
    }

    fun writeRecordedVersionCode(versionCode: Int) {
        versionFile.parentFile?.mkdirs()
        versionFile.writeText(versionCode.toString())
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
