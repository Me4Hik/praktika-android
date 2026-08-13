// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsLegacyFirstMigrationAcceleratedInstrumentedTest {
    @Test
    fun missingVersionMarker_appliesLegacyCleanupOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val diagnosticsDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val eventsFile = File(diagnosticsDir, "events.jsonl")
        val versionFile = File(diagnosticsDir, "recorded_version_code.txt")

        diagnosticsDir.deleteRecursively()
        diagnosticsDir.mkdirs()
        seedLegacyEventsWithoutMarker(eventsFile)
        assertFalse(versionFile.exists())

        DiagnosticsRecorder.resetForTests()
        DiagnosticsRecorder.initialize(context.applicationContext)

        val afterFirstInit = eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
        assertTrue(afterFirstInit.any { it.name == "diagnostics_version_boundary" })
        assertEquals("unknown", afterFirstInit.last { it.name == "diagnostics_version_boundary" }.metadata["previous_version_code"])
        assertEquals(
            BuildConfig.VERSION_CODE.toString(),
            afterFirstInit.last { it.name == "diagnostics_version_boundary" }.metadata["current_version_code"],
        )
        assertEquals(
            "legacy_without_version_marker",
            afterFirstInit.last { it.name == "diagnostics_version_boundary" }.metadata["migration_source"],
        )
        assertTrue(afterFirstInit.any { it.category == DiagnosticCategory.PERMISSION })
        assertTrue(afterFirstInit.any { it.category == DiagnosticCategory.USER })
        assertTrue(afterFirstInit.any { it.name == "uncaught_exception" })
        assertTrue(afterFirstInit.count { it.category == DiagnosticCategory.NOTIFICATION } <= 15)
        assertTrue(versionFile.exists())
        assertEquals(BuildConfig.VERSION_CODE.toString(), versionFile.readText().trim())

        val boundaryCountAfterFirst = afterFirstInit.count { it.name == "diagnostics_version_boundary" }

        DiagnosticsRecorder.resetForTests()
        DiagnosticsRecorder.initialize(context.applicationContext)

        val afterSecondInit = eventsFile.readLines().mapNotNull { DiagnosticEvent.fromJsonLine(it) }
        assertEquals(
            boundaryCountAfterFirst,
            afterSecondInit.count { it.name == "diagnostics_version_boundary" },
        )
        assertEquals(BuildConfig.VERSION_CODE.toString(), versionFile.readText().trim())
    }

    private fun seedLegacyEventsWithoutMarker(eventsFile: File) {
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "DISABLED",
            "cancel_notification" to "true",
            "show_notification" to "false",
            "alarm_count" to "0",
        )
        var seq = 0L
        fun buildEvent(
            category: DiagnosticCategory,
            name: String,
            metadata: Map<String, String> = emptyMap(),
        ): DiagnosticEvent {
            seq += 1
            return DiagnosticEvent(
                seq = seq,
                tsEpochMs = seq,
                monoMs = seq,
                category = category,
                name = name,
                metadata = metadata,
            )
        }
        val legacy = buildList {
            repeat(350) {
                add(buildEvent(DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            repeat(4) { add(buildEvent(DiagnosticCategory.PERMISSION, "notification_permission_state")) }
            repeat(3) { add(buildEvent(DiagnosticCategory.USER, "button_tap")) }
            add(buildEvent(DiagnosticCategory.ERROR, "uncaught_exception", mapOf("message" to "legacy")))
        }
        eventsFile.writeText(legacy.joinToString("\n") { it.toJsonLine() })
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
