// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt131 device test harness
package com.me4hik.praktika.diagnostics

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.Stage12BlackviewE2ESupport
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import java.io.File
import org.json.JSONObject

/**
 * Shared accelerated-only sandbox reset and artifact helpers.
 *
 * Reset order (full sandbox, before instrumentation method body):
 * 1. alarm experiment overrides
 * 2. runtime / init gate
 * 3. notification permission prefs (DataStore only; no shell revoke)
 * 4. accelerated time + diagnostics files
 * 5. Room DB file
 * 6. diagnostics recorder re-init (fresh events.jsonl)
 *
 * Shell pm grant/revoke and appops must run only from host script between instrumentation invocations.
 */
object AcceleratedDeviceTestHarness {
    const val PROMPT131_DIR = "prompt131"
    const val PROMPT127_DIR = "prompt127"

    fun deviceArtifactSlug(): String {
        return "${Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')}_${Build.VERSION.SDK_INT}"
    }

    fun acceleratedPackage(): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.packageName
    }

    fun resetSandboxFull() {
        AndroidAlarmScheduler.experimentApiModeOverride = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        // Shell pm revoke/appops deny kills the instrumentation host on Android 15.
        // Permission and exact-alarm preflight must run outside instrumentation (see prompt131 script).
        Stage12BlackviewE2ESupport.clearNotificationPermissionFlag()
        deleteAcceleratedArtifacts(context)
        reinitializeDiagnostics(context)
    }

    fun resetRuntimeOnly() {
        AndroidAlarmScheduler.experimentApiModeOverride = null
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
    }

    fun reinitializeDiagnostics(context: Context) {
        if (DiagnosticsRecorder.isInitialized()) {
            DiagnosticsRecorder.resetForTests()
        }
        DiagnosticsRecorder.initialize(context.applicationContext)
    }

    fun deleteAcceleratedArtifacts(context: Context) {
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME || it.name == "diagnostics" }
            ?.forEach { entry ->
                if (entry.isDirectory) {
                    entry.deleteRecursively()
                } else {
                    entry.delete()
                }
            }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    fun prompt131Root(context: Context): File {
        return File(context.getExternalFilesDir(null), PROMPT131_DIR).also { it.mkdirs() }
    }

    fun exportPrompt131(label: String, payload: JSONObject) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(prompt131Root(context), "$label.json")
        file.writeText(payload.toString(2))
        android.util.Log.i("PROMPT131_$label", payload.toString())
    }

    fun exportPrompt131Text(label: String, text: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(prompt131Root(context), "$label.txt").writeText(text)
    }

    fun readPrompt131Json(name: String): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(prompt131Root(context), name)
        check(file.exists()) { "Missing prompt131 artifact: ${file.absolutePath}" }
        return JSONObject(file.readText())
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
