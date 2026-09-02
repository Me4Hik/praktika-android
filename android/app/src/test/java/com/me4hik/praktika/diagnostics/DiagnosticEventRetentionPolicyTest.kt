// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventRetentionPolicyTest {
    private var seq = 0L

    private fun event(
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

    @Test
    fun trim_retainsPermissionUserAndErrorWhenNotificationDominates() {
        seq = 0
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "ENABLED",
            "cancel_notification" to "false",
            "show_notification" to "false",
            "alarm_count" to "1",
        )
        val events = buildList {
            repeat(500) {
                add(event(DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            repeat(5) { add(event(DiagnosticCategory.PERMISSION, "notification_permission_state")) }
            repeat(3) { add(event(DiagnosticCategory.USER, "button_tap")) }
            add(event(DiagnosticCategory.ERROR, "caught_exception"))
            add(event(DiagnosticCategory.ERROR, "uncaught_exception"))
        }

        val trimmed = DiagnosticEventRetentionPolicy.trimToCapacity(events, maxEvents = 500)

        assertTrue(trimmed.size <= 500)
        assertEquals(5, trimmed.count { it.category == DiagnosticCategory.PERMISSION })
        assertEquals(3, trimmed.count { it.category == DiagnosticCategory.USER })
        assertEquals(2, trimmed.count { it.category == DiagnosticCategory.ERROR })
        assertTrue(trimmed.count { it.category == DiagnosticCategory.NOTIFICATION } <= 250)
    }

    @Test
    fun versionBoundary_reducesLegacyNotificationNoiseAndKeepsCapacity() {
        seq = 0
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "DISABLED",
            "cancel_notification" to "true",
            "show_notification" to "false",
            "alarm_count" to "0",
        )
        val legacy = buildList {
            repeat(400) {
                add(event(DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            repeat(3) { add(event(DiagnosticCategory.PERMISSION, "permission_ui_refresh_trigger")) }
            repeat(2) { add(event(DiagnosticCategory.USER, "button_tap")) }
            add(event(DiagnosticCategory.ERROR, "uncaught_exception"))
        }

        val cleaned = DiagnosticEventRetentionPolicy.applyVersionBoundary(
            events = legacy,
            currentVersionCode = 5,
            maxEvents = 500,
        )

        assertTrue(cleaned.size <= 500)
        assertTrue(cleaned.size <= DiagnosticEventRetentionPolicy.VERSION_BOUNDARY_TARGET_EVENTS)
        assertTrue(cleaned.count { it.category == DiagnosticCategory.NOTIFICATION } <= 15)
        assertTrue(cleaned.any { it.category == DiagnosticCategory.PERMISSION })
        assertTrue(cleaned.any { it.category == DiagnosticCategory.USER })
        assertTrue(cleaned.any { it.name == "uncaught_exception" })
    }

    @Test
    fun versionBoundary_retainsPreviousUncaughtException() {
        seq = 0
        val legacy = listOf(
            event(DiagnosticCategory.ERROR, "uncaught_exception", mapOf("message" to "boom")),
            event(DiagnosticCategory.NOTIFICATION, "notification_sync_started"),
        ) + List(200) {
            event(
                DiagnosticCategory.NOTIFICATION,
                "notification_sync_result",
                mapOf("reason" to "FOREGROUND"),
            )
        }

        val cleaned = DiagnosticEventRetentionPolicy.applyVersionBoundary(
            events = legacy,
            currentVersionCode = 4,
            maxEvents = 500,
        )

        assertTrue(cleaned.any { it.name == "uncaught_exception" })
    }

    @Test
    fun trimAndSelectForReport_preserveChronologicalOrdering() {
        seq = 0
        val events = buildList {
            add(event(DiagnosticCategory.APP, "process_start"))
            add(event(DiagnosticCategory.PERMISSION, "permission_result"))
            add(event(DiagnosticCategory.USER, "button_tap"))
            add(event(DiagnosticCategory.NOTIFICATION, "notification_sync_started"))
            add(event(DiagnosticCategory.ERROR, "caught_exception"))
        }

        val trimmed = DiagnosticEventRetentionPolicy.trimToCapacity(events, maxEvents = 500)
        val selected = DiagnosticEventRetentionPolicy.selectForReport(trimmed, limit = 150)

        assertEquals(events.map { it.seq }, trimmed.map { it.seq })
        assertEquals(trimmed.map { it.seq }, selected.map { it.seq })
    }

    @Test
    fun trim_keepsBufferBounded() {
        seq = 0
        val events = List(800) {
            event(DiagnosticCategory.NOTIFICATION, "notification_sync_started")
        }

        val trimmed = DiagnosticEventRetentionPolicy.trimToCapacity(events, maxEvents = 500)

        assertTrue(trimmed.size <= 500)
        assertEquals(250, trimmed.size)
        assertTrue(trimmed.zipWithNext().all { (left, right) -> left.seq <= right.seq })
    }

    @Test
    fun selectForReport_includesPermissionUserAndErrorMix() {
        seq = 0
        val events = buildList {
            repeat(300) { add(event(DiagnosticCategory.NOTIFICATION, "notification_sync_result")) }
            repeat(8) { add(event(DiagnosticCategory.PERMISSION, "notification_permission_state")) }
            repeat(6) { add(event(DiagnosticCategory.USER, "button_tap")) }
            repeat(2) { add(event(DiagnosticCategory.ERROR, "caught_exception")) }
            repeat(20) { add(event(DiagnosticCategory.APP, "activity_resume")) }
        }

        val selected = DiagnosticEventRetentionPolicy.selectForReport(events, limit = 150)

        assertTrue(selected.size <= 150)
        assertTrue(selected.any { it.category == DiagnosticCategory.PERMISSION })
        assertTrue(selected.any { it.category == DiagnosticCategory.USER })
        assertTrue(selected.any { it.category == DiagnosticCategory.ERROR })
        assertTrue(selected.any { it.category == DiagnosticCategory.APP })
        assertTrue(selected.count { it.category == DiagnosticCategory.NOTIFICATION } <= 90)
        assertTrue(selected.zipWithNext().all { (left, right) -> left.seq <= right.seq })
    }

    @Test
    fun createVersionBoundaryEvent_containsVersionMetadata() {
        val boundary = DiagnosticEventRetentionPolicy.createVersionBoundaryEvent(
            seq = 42L,
            tsEpochMs = 100L,
            monoMs = 200L,
            previousVersionCode = 4,
            currentVersionCode = 5,
        )

        assertEquals("diagnostics_version_boundary", boundary.name)
        assertEquals("4", boundary.metadata["previous_version_code"])
        assertEquals("5", boundary.metadata["current_version_code"])
        assertTrue(!boundary.metadata.containsKey("migration_source"))
    }

    @Test
    fun createVersionBoundaryEvent_marksUnknownPreviousForLegacyMigration() {
        val boundary = DiagnosticEventRetentionPolicy.createVersionBoundaryEvent(
            seq = 1L,
            tsEpochMs = 100L,
            monoMs = 200L,
            previousVersionCode = null,
            currentVersionCode = 6,
        )

        assertEquals("unknown", boundary.metadata["previous_version_code"])
        assertEquals("6", boundary.metadata["current_version_code"])
        assertEquals("legacy_without_version_marker", boundary.metadata["migration_source"])
    }

    @Test
    fun trimAndSelectForReport_retainSemanticTraceEventsWhenNotificationDominates() {
        seq = 0
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "ENABLED",
            "cancel_notification" to "false",
            "show_notification" to "false",
            "alarm_count" to "1",
        )
        val events = buildList {
            repeat(400) {
                add(event(DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            add(
                event(
                    DiagnosticCategory.USER,
                    "answer_save_attempt",
                    mapOf("occurrence_id" to "6", "draft_blank" to "false"),
                ),
            )
            add(
                event(
                    DiagnosticCategory.USER,
                    "answer_save_result",
                    mapOf("occurrence_id" to "6", "result" to "success"),
                ),
            )
            add(
                event(
                    DiagnosticCategory.NOTIFICATION,
                    "notification_alarm_scheduled",
                    mapOf("occurrence_id" to "6", "alarm_type" to "planned"),
                ),
            )
            add(
                event(
                    DiagnosticCategory.NOTIFICATION,
                    "notification_post_result",
                    mapOf("occurrence_id" to "6", "result" to "posted"),
                ),
            )
            add(
                event(
                    DiagnosticCategory.APP,
                    "schedule_changed",
                    mapOf("active_occurrence_id" to "6"),
                ),
            )
        }

        val trimmed = DiagnosticEventRetentionPolicy.trimToCapacity(events, maxEvents = 500)
        val selected = DiagnosticEventRetentionPolicy.selectForReport(trimmed, limit = 150)

        assertTrue(trimmed.any { it.name == "answer_save_attempt" })
        assertTrue(trimmed.any { it.name == "answer_save_result" })
        assertTrue(trimmed.any { it.name == "notification_alarm_scheduled" })
        assertTrue(trimmed.any { it.name == "notification_post_result" })
        assertTrue(trimmed.any { it.name == "schedule_changed" })
        assertTrue(selected.any { it.name == "answer_save_attempt" })
        assertTrue(selected.any { it.name == "answer_save_result" })
        assertTrue(selected.any { it.name == "notification_alarm_scheduled" })
        assertTrue(selected.any { it.name == "notification_post_result" })
        assertTrue(selected.any { it.name == "schedule_changed" })
    }

    @Test
    fun selectForReport_retainsSystemEventReceivedAmongNotificationFlood() {
        seq = 0
        val syncMetadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "ENABLED",
            "cancel_notification" to "false",
            "show_notification" to "false",
            "alarm_count" to "1",
        )
        val events = buildList {
            repeat(400) {
                add(event(DiagnosticCategory.NOTIFICATION, "notification_sync_result", syncMetadata))
            }
            add(
                event(
                    DiagnosticCategory.NOTIFICATION,
                    "system_event_received",
                    mapOf(
                        "action" to "android.intent.action.BOOT_COMPLETED",
                        "sync_reason" to "BOOT",
                        "wall_clock_ms" to "10",
                        "elapsed_realtime_ms" to "20",
                    ),
                ),
            )
        }

        val selected = DiagnosticEventRetentionPolicy.selectForReport(events, limit = 150)
        assertTrue(selected.any { it.name == "system_event_received" })
        assertEquals(
            "BOOT",
            selected.first { it.name == "system_event_received" }.metadata["sync_reason"],
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
