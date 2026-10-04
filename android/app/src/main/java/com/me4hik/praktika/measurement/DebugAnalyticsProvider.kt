package com.me4hik.praktika.measurement

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugAnalyticsEntry(
    val sequence: Long,
    val timestampEpochMillis: Long,
    val eventName: String,
    val params: Map<String, String>,
    val routedProviders: List<String>,
    val deliveryStatus: String,
    val debugOnly: Boolean,
)

/**
 * In-memory ring buffer for measurement diagnostics. Separate from DiagnosticsRecorder.
 */
class DebugAnalyticsProvider(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val routingPolicy: AnalyticsRoutingPolicy = AnalyticsRoutingPolicy.phase1(),
) : AnalyticsProvider {
    override val id: AnalyticsProviderId = AnalyticsProviderId.DEBUG

    private val lock = Any()
    private val buffer = ArrayDeque<DebugAnalyticsEntry>(capacity)
    private var nextSequence = 1L
    private val _entries = MutableStateFlow<List<DebugAnalyticsEntry>>(emptyList())
    val entries: StateFlow<List<DebugAnalyticsEntry>> = _entries.asStateFlow()

    private val listeners = CopyOnWriteArrayList<(DebugAnalyticsEntry) -> Unit>()

    override fun track(event: AnalyticsEvent) {
        val intended = routingPolicy.intendedProviders(event)
            .map { it.name }
            .sorted()
        val entry = synchronized(lock) {
            val created = DebugAnalyticsEntry(
                sequence = nextSequence++,
                timestampEpochMillis = clock(),
                eventName = event.name,
                params = event.params.asMap(),
                routedProviders = intended,
                deliveryStatus = STATUS_RECORDED,
                debugOnly = event.debugOnly,
            )
            buffer.addLast(created)
            while (buffer.size > capacity) {
                buffer.removeFirst()
            }
            _entries.value = buffer.toList()
            created
        }
        listeners.forEach { listener ->
            runCatching { listener(entry) }
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    fun snapshot(): List<DebugAnalyticsEntry> = synchronized(lock) { buffer.toList() }

    fun addListener(listener: (DebugAnalyticsEntry) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (DebugAnalyticsEntry) -> Unit) {
        listeners -= listener
    }

    companion object {
        const val DEFAULT_CAPACITY = 100
        const val STATUS_RECORDED = "recorded"
    }
}
