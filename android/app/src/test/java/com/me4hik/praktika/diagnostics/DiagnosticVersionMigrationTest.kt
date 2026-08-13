// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticVersionMigrationTest {
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        DiagnosticsRecorder.resetForTests()
    }

    @After
    fun tearDown() {
        DiagnosticsRecorder.resetForTests()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun newTempFile(name: String): File {
        val file = File.createTempFile(name, ".tmp")
        tempFiles.add(file)
        return file
    }

    private fun event(
        seq: Long,
        category: DiagnosticCategory,
        name: String,
        metadata: Map<String, String> = emptyMap(),
    ): DiagnosticEvent {
        return DiagnosticEvent(
            seq = seq,
            tsEpochMs = seq,
            monoMs = seq,
            category = category,
            name = name,
            metadata = metadata,
        )
    }

    @Test
    fun shouldApplyVersionBoundary_legacyBufferWithoutMarker() {
        val legacy = listOf(event(1, DiagnosticCategory.NOTIFICATION, "notification_sync_result"))
        assertTrue(
            DiagnosticVersionMigration.shouldApplyVersionBoundary(
                recordedVersionCode = null,
                currentVersionCode = 6,
                existingEvents = legacy,
            ),
        )
    }

    @Test
    fun shouldApplyVersionBoundary_freshInstallWithoutMarker() {
        assertFalse(
            DiagnosticVersionMigration.shouldApplyVersionBoundary(
                recordedVersionCode = null,
                currentVersionCode = 6,
                existingEvents = emptyList(),
            ),
        )
    }

    @Test
    fun shouldApplyVersionBoundary_markerUpgrade() {
        assertTrue(
            DiagnosticVersionMigration.shouldApplyVersionBoundary(
                recordedVersionCode = 5,
                currentVersionCode = 6,
                existingEvents = emptyList(),
            ),
        )
    }

    @Test
    fun shouldApplyVersionBoundary_sameVersionIsIdempotent() {
        assertFalse(
            DiagnosticVersionMigration.shouldApplyVersionBoundary(
                recordedVersionCode = 6,
                currentVersionCode = 6,
                existingEvents = listOf(event(1, DiagnosticCategory.APP, "process_start")),
            ),
        )
    }

    @Test
    fun migrateIfNeeded_legacyBufferWithoutMarker_trimsNoiseAndWritesBoundary() {
        val eventsFile = newTempFile("legacy-no-marker-events")
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "DISABLED",
            "cancel_notification" to "true",
            "show_notification" to "false",
            "alarm_count" to "0",
        )
        val legacy = buildList {
            repeat(350) { index ->
                add(event(index + 1L, DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            repeat(4) { index ->
                add(event(400L + index, DiagnosticCategory.PERMISSION, "notification_permission_state"))
            }
            repeat(3) { index ->
                add(event(500L + index, DiagnosticCategory.USER, "button_tap"))
            }
            add(event(600L, DiagnosticCategory.ERROR, "uncaught_exception"))
        }
        eventsFile.writeText(legacy.joinToString("\n") { it.toJsonLine() })

        val store = DiagnosticEventStore(eventsFile, initialSeq = 600L)
        val applied = DiagnosticVersionMigration.migrateIfNeeded(
            store = store,
            recordedVersionCode = null,
            currentVersionCode = 6,
            nowEpochMs = 1_000L,
            nowMonoMs = 2_000L,
        )

        assertTrue(applied)
        val migrated = store.readAll()
        assertTrue(migrated.any { it.name == "diagnostics_version_boundary" })
        assertEquals("unknown", migrated.last { it.name == "diagnostics_version_boundary" }.metadata["previous_version_code"])
        assertEquals("6", migrated.last { it.name == "diagnostics_version_boundary" }.metadata["current_version_code"])
        assertEquals(
            "legacy_without_version_marker",
            migrated.last { it.name == "diagnostics_version_boundary" }.metadata["migration_source"],
        )
        assertTrue(migrated.any { it.category == DiagnosticCategory.PERMISSION })
        assertTrue(migrated.any { it.category == DiagnosticCategory.USER })
        assertTrue(migrated.any { it.name == "uncaught_exception" })
        assertTrue(migrated.count { it.category == DiagnosticCategory.NOTIFICATION } <= 15)
        assertTrue(migrated.size <= DiagnosticEventRetentionPolicy.VERSION_BOUNDARY_TARGET_EVENTS)
    }

    @Test
    fun migrateIfNeeded_freshInstallWithoutMarker_skipsCleanup() {
        val eventsFile = newTempFile("fresh-events")
        val store = DiagnosticEventStore(eventsFile)

        val applied = DiagnosticVersionMigration.migrateIfNeeded(
            store = store,
            recordedVersionCode = null,
            currentVersionCode = 6,
            nowEpochMs = 1_000L,
            nowMonoMs = 2_000L,
        )

        assertFalse(applied)
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun migrateIfNeeded_markerUpgrade_appliesNormalBoundary() {
        val eventsFile = newTempFile("marker-upgrade-events")
        val legacy = listOf(
            event(1, DiagnosticCategory.NOTIFICATION, "notification_sync_result"),
            event(2, DiagnosticCategory.PERMISSION, "notification_permission_state"),
        )
        eventsFile.writeText(legacy.joinToString("\n") { it.toJsonLine() })
        val store = DiagnosticEventStore(eventsFile, initialSeq = 2L)

        val applied = DiagnosticVersionMigration.migrateIfNeeded(
            store = store,
            recordedVersionCode = 5,
            currentVersionCode = 6,
            nowEpochMs = 1_000L,
            nowMonoMs = 2_000L,
        )

        assertTrue(applied)
        val boundary = store.readAll().single { it.name == "diagnostics_version_boundary" }
        assertEquals("5", boundary.metadata["previous_version_code"])
        assertEquals("6", boundary.metadata["current_version_code"])
        assertFalse(boundary.metadata.containsKey("migration_source"))
    }

    @Test
    fun migrateIfNeeded_sameMarker_doesNotRepeatBoundary() {
        val eventsFile = newTempFile("same-marker-events")
        val existing = listOf(
            event(1, DiagnosticCategory.APP, "process_start"),
            event(2, DiagnosticCategory.APP, "diagnostics_version_boundary"),
        )
        eventsFile.writeText(existing.joinToString("\n") { it.toJsonLine() })
        val store = DiagnosticEventStore(eventsFile, initialSeq = 2L)

        val applied = DiagnosticVersionMigration.migrateIfNeeded(
            store = store,
            recordedVersionCode = 6,
            currentVersionCode = 6,
            nowEpochMs = 1_000L,
            nowMonoMs = 2_000L,
        )

        assertFalse(applied)
        assertEquals(2, store.readAll().size)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
