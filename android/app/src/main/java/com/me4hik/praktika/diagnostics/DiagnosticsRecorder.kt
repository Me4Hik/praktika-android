// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import android.content.Context
import android.os.SystemClock
import com.me4hik.praktika.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DiagnosticsRecorder private constructor(
    private val eventStore: DiagnosticEventStore,
) {
    fun record(
        category: DiagnosticCategory,
        name: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val event = buildEvent(category, name, metadata)
        eventStore.enqueue(event)
    }

    fun recordSync(
        category: DiagnosticCategory,
        name: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val event = buildEvent(category, name, metadata)
        eventStore.appendSync(event)
    }

    fun recordCaughtException(source: String, exception: Throwable) {
        record(
            category = DiagnosticCategory.ERROR,
            name = "caught_exception",
            metadata = mapOf(
                "source" to source,
                "exception_class" to exception.javaClass.name,
                "message" to (exception.message?.take(200) ?: ""),
            ),
        )
    }

    fun getRecentEvents(limit: Int): List<DiagnosticEvent> {
        val buffered = eventStore.readRecent(DiagnosticEventStore.MAX_EVENTS)
        return DiagnosticEventRetentionPolicy.selectForReport(buffered, limit)
    }

    fun getBufferedEvents(): List<DiagnosticEvent> = eventStore.readAll()

    fun getMostRecentError(): DiagnosticEvent? {
        return eventStore.readRecent(DiagnosticEventStore.MAX_EVENTS)
            .lastOrNull { it.category == DiagnosticCategory.ERROR }
    }

    private fun buildEvent(
        category: DiagnosticCategory,
        name: String,
        metadata: Map<String, String>,
    ): DiagnosticEvent {
        return DiagnosticEvent(
            seq = eventStore.nextSeq(),
            tsEpochMs = System.currentTimeMillis(),
            monoMs = SystemClock.elapsedRealtime(),
            category = category,
            name = name,
            metadata = DiagnosticMetadataPolicy.sanitize(metadata),
        )
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        @Volatile
        private var instance: DiagnosticsRecorder? = null

        fun initialize(context: Context) {
            if (initialized.get()) {
                return
            }
            synchronized(this) {
                if (initialized.get()) {
                    return
                }
                val eventsFile = File(context.filesDir, "diagnostics/events.jsonl")
                val versionStore = DiagnosticVersionStore(context)
                val previousVersionCode = versionStore.readRecordedVersionCode()
                val currentVersionCode = BuildConfig.VERSION_CODE
                val initialSeq = DiagnosticEventStore.loadInitialSeq(eventsFile)
                val store = DiagnosticEventStore(eventsFile, initialSeq = initialSeq)
                DiagnosticVersionMigration.migrateIfNeeded(
                    store = store,
                    recordedVersionCode = previousVersionCode,
                    currentVersionCode = currentVersionCode,
                    nowEpochMs = System.currentTimeMillis(),
                    nowMonoMs = SystemClock.elapsedRealtime(),
                )
                versionStore.writeRecordedVersionCode(currentVersionCode)
                instance = DiagnosticsRecorder(store)
                initialized.set(true)
            }
        }

        fun get(): DiagnosticsRecorder {
            return requireNotNull(instance) {
                "DiagnosticsRecorder.initialize() must be called before use"
            }
        }

        fun isInitialized(): Boolean = initialized.get()

        internal fun createForTests(eventsFile: File, initialSeq: Long = 0L): DiagnosticsRecorder {
            return DiagnosticsRecorder(
                DiagnosticEventStore(eventsFile, initialSeq = initialSeq),
            )
        }

        internal fun installForTests(eventsFile: File, initialSeq: Long = 0L) {
            synchronized(this) {
                instance = createForTests(eventsFile, initialSeq)
                initialized.set(true)
            }
        }

        internal fun resetForTests() {
            synchronized(this) {
                instance = null
                initialized.set(false)
            }
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
