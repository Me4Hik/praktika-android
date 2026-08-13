// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class DiagnosticEventStore(
    private val eventsFile: File,
    private val maxEvents: Int = MAX_EVENTS,
    initialSeq: Long = 0L,
) {
    private val seqCounter = AtomicLong(initialSeq)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeChannel = Channel<DiagnosticEvent>(capacity = Channel.BUFFERED)

    init {
        eventsFile.parentFile?.mkdirs()
        if (!eventsFile.exists()) {
            eventsFile.createNewFile()
        }
        scope.launch {
            for (event in writeChannel) {
                appendEventInternal(event)
            }
        }
    }

    fun nextSeq(): Long = seqCounter.incrementAndGet()

    fun currentSeq(): Long = seqCounter.get()

    fun enqueue(event: DiagnosticEvent) {
        writeChannel.trySend(event)
    }

    fun appendSync(event: DiagnosticEvent) {
        synchronized(this) {
            appendEventInternal(event)
        }
    }

    fun readRecent(limit: Int): List<DiagnosticEvent> {
        synchronized(this) {
            return readAll().takeLast(limit)
        }
    }

    fun readAll(): List<DiagnosticEvent> {
        synchronized(this) {
            if (!eventsFile.exists() || eventsFile.length() == 0L) {
                return emptyList()
            }
            return eventsFile.readLines()
                .mapNotNull { line -> DiagnosticEvent.fromJsonLine(line) }
        }
    }

    fun replaceAll(events: List<DiagnosticEvent>) {
        synchronized(this) {
            eventsFile.parentFile?.mkdirs()
            if (events.isEmpty()) {
                eventsFile.writeText("")
                return
            }
            eventsFile.writeText(events.joinToString(separator = "\n") { it.toJsonLine() })
        }
    }

    private fun appendEventInternal(event: DiagnosticEvent) {
        eventsFile.parentFile?.mkdirs()
        val existing = if (eventsFile.exists() && eventsFile.length() > 0L) {
            eventsFile.readLines().mapNotNull { line -> DiagnosticEvent.fromJsonLine(line) }
        } else {
            emptyList()
        }
        val coalescedEvent = coalesceWithPrevious(existing.lastOrNull(), event)
        val updated = DiagnosticEventRetentionPolicy.trimToCapacity(
            events = if (coalescedEvent != null) {
                existing.dropLast(1) + coalescedEvent
            } else {
                existing + event
            },
            maxEvents = maxEvents,
        )
        eventsFile.writeText(updated.joinToString(separator = "\n") { it.toJsonLine() })
    }

    private fun coalesceWithPrevious(
        previous: DiagnosticEvent?,
        incoming: DiagnosticEvent,
    ): DiagnosticEvent? {
        if (previous == null) {
            return null
        }
        if (previous.category != incoming.category || previous.name != incoming.name) {
            return null
        }
        if (incoming.name !in COALESCABLE_EVENT_NAMES) {
            return null
        }
        if (incoming.tsEpochMs - previous.tsEpochMs > COALESCE_WINDOW_MS) {
            return null
        }
        val previousComparable = comparableMetadata(previous.metadata)
        val incomingComparable = comparableMetadata(incoming.metadata)
        if (previousComparable != incomingComparable) {
            return null
        }
        val repeatCount = previous.metadata["repeat_count"]?.toIntOrNull()?.plus(1) ?: 2
        val firstTs = previous.metadata["first_ts_epoch_ms"] ?: previous.tsEpochMs.toString()
        return incoming.copy(
            seq = previous.seq,
            metadata = incomingComparable + mapOf(
                "repeat_count" to repeatCount.toString(),
                "first_ts_epoch_ms" to firstTs,
                "last_ts_epoch_ms" to incoming.tsEpochMs.toString(),
            ),
        )
    }

    private fun comparableMetadata(metadata: Map<String, String>): Map<String, String> {
        return metadata.filterKeys { key ->
            key !in COALESCE_METADATA_KEYS
        }
    }

    companion object {
        const val MAX_EVENTS = 500
        private const val COALESCE_WINDOW_MS = 5_000L
        private val COALESCABLE_EVENT_NAMES = setOf("notification_sync_result")
        private val COALESCE_METADATA_KEYS = setOf(
            "repeat_count",
            "first_ts_epoch_ms",
            "last_ts_epoch_ms",
        )

        fun loadInitialSeq(eventsFile: File): Long {
            if (!eventsFile.exists()) {
                return 0L
            }
            return eventsFile.readLines()
                .mapNotNull { line -> DiagnosticEvent.fromJsonLine(line) }
                .maxOfOrNull { it.seq }
                ?: 0L
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
