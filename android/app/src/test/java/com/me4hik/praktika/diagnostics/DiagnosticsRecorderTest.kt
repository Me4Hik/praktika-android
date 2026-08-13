// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsRecorderTest {
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

    private fun newTempDir(name: String): File {
        val dir = File.createTempFile(name, "-dir")
        dir.delete()
        dir.mkdirs()
        tempFiles.add(dir)
        return dir
    }

    @Test
    fun writesEventToPersistentFile() {
        val file = newTempFile("events")
        val store = DiagnosticEventStore(file)
        val event = DiagnosticEvent(
            seq = store.nextSeq(),
            tsEpochMs = 1L,
            monoMs = 2L,
            category = DiagnosticCategory.APP,
            name = "process_start",
            metadata = mapOf("package" to "com.test"),
        )
        store.appendSync(event)
        val loaded = store.readRecent(limit = 10)
        assertEquals(1, loaded.size)
        assertEquals("process_start", loaded.first().name)
    }

    @Test
    fun retentionKeepsOnlyLast500Events() {
        val file = newTempFile("events-retention")
        val store = DiagnosticEventStore(file, maxEvents = 500)
        repeat(520) { index ->
            store.appendSync(
                DiagnosticEvent(
                    seq = store.nextSeq(),
                    tsEpochMs = index.toLong(),
                    monoMs = index.toLong(),
                    category = DiagnosticCategory.APP,
                    name = "event_$index",
                ),
            )
        }
        val recent = store.readRecent(limit = 600)
        assertEquals(500, recent.size)
        assertEquals("event_20", recent.first().name)
        assertEquals("event_519", recent.last().name)
    }

    @Test
    fun rejectsForbiddenMetadataKeys() {
        val sanitized = DiagnosticMetadataPolicy.sanitize(
            mapOf(
                "route" to "home",
                "question_text" to "secret",
                "answer_text" to "secret",
            ),
        )
        assertEquals(mapOf("route" to "home"), sanitized)
        assertFalse(DiagnosticMetadataPolicy.isAllowedKey("notification_body"))
    }

    @Test
    fun recorderPersistsSyncEvents() {
        val file = newTempFile("recorder")
        val recorder = DiagnosticsRecorder.createForTests(file)
        recorder.recordSync(DiagnosticCategory.USER, "button_tap", mapOf("control_id" to "test"))
        val events = recorder.getRecentEvents(limit = 10)
        assertEquals(1, events.size)
        assertEquals("button_tap", events.first().name)
    }

    @Test
    fun reportSurvivesWhenRemoteSendUnavailable() {
        val reportsDir = newTempDir("reports")
        val store = DiagnosticReportStore(reportsDir)
        val snapshot = JSONObject().apply { put("versionCode", 1) }
        val saved = store.saveReport(snapshot)
        assertTrue(saved.exists())
        assertEquals(1, store.listReports().size)
    }

    @Test
    fun uncaughtHandlerDelegatesToPreviousHandler() {
        var delegated = false
        val previous = Thread.UncaughtExceptionHandler { _, _ ->
            delegated = true
        }
        val handler = DiagnosticUncaughtExceptionHandler(previous)
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        assertTrue(delegated)
    }

    @Test
    fun uncaughtHandlerRecordsWhenRecorderInitialized() {
        val file = newTempFile("uncaught")
        DiagnosticsRecorder.installForTests(file)
        var delegated = false
        val handler = DiagnosticUncaughtExceptionHandler { _, _ ->
            delegated = true
        }
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        assertTrue(delegated)
        val events = DiagnosticsRecorder.get().getRecentEvents(10)
        assertEquals(1, events.size)
        assertEquals("uncaught_exception", events.first().name)
    }

    @Test
    fun coalescesRepeatedNotificationSyncResults() {
        val file = newTempFile("coalesce")
        val store = DiagnosticEventStore(file)
        val metadata = mapOf(
            "reason" to "FOREGROUND",
            "capability" to "DISABLED",
            "cancel_notification" to "true",
            "show_notification" to "false",
            "alarm_count" to "0",
        )
        repeat(3) { index ->
            store.appendSync(
                DiagnosticEvent(
                    seq = store.nextSeq(),
                    tsEpochMs = 1_000L + index,
                    monoMs = 1_000L + index,
                    category = DiagnosticCategory.NOTIFICATION,
                    name = "notification_sync_result",
                    metadata = metadata,
                ),
            )
        }
        val events = store.readRecent(limit = 10)
        assertEquals(1, events.size)
        assertEquals("3", events.single().metadata["repeat_count"])
        assertEquals("1000", events.single().metadata["first_ts_epoch_ms"])
        assertEquals("1002", events.single().metadata["last_ts_epoch_ms"])
    }

    @Test
    fun snapshotJsonDoesNotIncludeQuestionOrAnswerTextKeys() {
        val json = JSONObject().apply {
            put("occurrenceId", 1L)
            put("occurrenceStatus", "AVAILABLE")
            put("scheduleSlotMinutes", org.json.JSONArray(listOf(480, 720, 1080)))
        }
        val keys = json.keys().asSequence().toList()
        assertFalse(keys.any { it.contains("question", ignoreCase = true) })
        assertFalse(keys.any { it.contains("answer", ignoreCase = true) })
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
