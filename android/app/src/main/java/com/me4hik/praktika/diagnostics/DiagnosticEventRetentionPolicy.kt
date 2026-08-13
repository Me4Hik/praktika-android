// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

internal object DiagnosticEventRetentionPolicy {
    const val ERROR_CAP = 20
    const val PERMISSION_CAP = 15
    const val USER_CAP = 10
    const val APP_CAP = 80
    const val LEGACY_NOTIFICATION_KEEP = 15
    const val VERSION_BOUNDARY_TARGET_EVENTS = 350
    const val REPORT_PERMISSION_CAP = 20
    const val REPORT_USER_CAP = 15
    const val REPORT_SEMANTIC_TRACE_CAP = 25
    const val REPORT_NOTIFICATION_MAX_FRACTION = 0.60
    const val SEMANTIC_TRACE_CAP = 25

    private val CRASH_EVENT_NAMES = setOf("uncaught_exception", "caught_exception")
    private val SEMANTIC_TRACE_EVENT_NAMES = TargetedBugDiagnostics.SEMANTIC_TRACE_EVENT_NAMES
    private val LEGACY_NOTIFICATION_NOISE_NAMES = setOf(
        "notification_sync_started",
        "notification_sync_result",
    )

    fun trimToCapacity(events: List<DiagnosticEvent>, maxEvents: Int): List<DiagnosticEvent> {
        if (events.size <= maxEvents) {
            return events.sortedBy { it.seq }
        }
        val sorted = events.sortedBy { it.seq }
        val pinned = buildPinnedEvents(sorted)
        val pinnedSeqs = pinned.map { it.seq }.toSet()
        val unpinned = sorted.filter { it.seq !in pinnedSeqs }

        val maxNotificationSlots = (maxEvents * NOTIFICATION_MAX_FRACTION).toInt()
        val remainingAfterPinned = (maxEvents - pinned.size).coerceAtLeast(0)

        val unpinnedNonNotification = unpinned.filter { it.category != DiagnosticCategory.NOTIFICATION }
        val unpinnedNotification = unpinned.filter { it.category == DiagnosticCategory.NOTIFICATION }

        val selectedNonNotification = unpinnedNonNotification.takeLast(remainingAfterPinned)
        val notificationSlots = (maxEvents - pinned.size - selectedNonNotification.size)
            .coerceIn(0, maxNotificationSlots)
        val selectedNotification = unpinnedNotification.takeLast(notificationSlots)

        return (pinned + selectedNonNotification + selectedNotification)
            .distinctBy { it.seq }
            .sortedBy { it.seq }
    }

    fun applyVersionBoundary(
        events: List<DiagnosticEvent>,
        currentVersionCode: Int,
        maxEvents: Int,
    ): List<DiagnosticEvent> {
        if (events.isEmpty()) {
            return events
        }
        val sorted = events.sortedBy { it.seq }
        val crashEvidence = sorted.filter { it.name in CRASH_EVENT_NAMES }
        val errors = sorted.filter {
            it.category == DiagnosticCategory.ERROR && it.name !in CRASH_EVENT_NAMES
        }.takeLast(5)
        val permissions = sorted.filter { it.category == DiagnosticCategory.PERMISSION }.takeLast(10)
        val users = sorted.filter { it.category == DiagnosticCategory.USER }.takeLast(5)
        val appEvents = sorted.filter { it.category == DiagnosticCategory.APP }.takeLast(20)
        val legacyNotifications = trimLegacyNotificationNoise(
            sorted.filter { it.category == DiagnosticCategory.NOTIFICATION },
            maxKeep = LEGACY_NOTIFICATION_KEEP,
        )

        val retained = (crashEvidence + errors + permissions + users + appEvents + legacyNotifications)
            .distinctBy { it.seq }
            .sortedBy { it.seq }

        val targetSize = VERSION_BOUNDARY_TARGET_EVENTS.coerceAtMost(maxEvents)
        return trimToCapacity(retained, targetSize.coerceAtMost(maxEvents))
            .sortedBy { it.seq }
    }

    fun createVersionBoundaryEvent(
        seq: Long,
        tsEpochMs: Long,
        monoMs: Long,
        previousVersionCode: Int?,
        currentVersionCode: Int,
    ): DiagnosticEvent {
        val metadata = buildMap {
            put("previous_version_code", previousVersionCode?.toString() ?: "unknown")
            put("current_version_code", currentVersionCode.toString())
            if (previousVersionCode == null) {
                put("migration_source", "legacy_without_version_marker")
            }
        }
        return DiagnosticEvent(
            seq = seq,
            tsEpochMs = tsEpochMs,
            monoMs = monoMs,
            category = DiagnosticCategory.APP,
            name = "diagnostics_version_boundary",
            metadata = metadata,
        )
    }

    fun selectForReport(events: List<DiagnosticEvent>, limit: Int): List<DiagnosticEvent> {
        if (events.isEmpty() || limit <= 0) {
            return emptyList()
        }
        val sorted = events.sortedBy { it.seq }
        if (sorted.size <= limit) {
            return sorted
        }

        val pinnedErrors = sorted.filter { it.category == DiagnosticCategory.ERROR }
        val pinnedPermissions = sorted.filter { it.category == DiagnosticCategory.PERMISSION }
            .takeLast(REPORT_PERMISSION_CAP)
        val pinnedUsers = sorted.filter { it.category == DiagnosticCategory.USER }
            .takeLast(REPORT_USER_CAP)
        val pinnedSemanticTrace = sorted.filter { it.name in SEMANTIC_TRACE_EVENT_NAMES }
            .takeLast(REPORT_SEMANTIC_TRACE_CAP)
        val pinned = (pinnedErrors + pinnedPermissions + pinnedUsers + pinnedSemanticTrace)
            .distinctBy { it.seq }
            .sortedBy { it.seq }

        val pinnedSeqs = pinned.map { it.seq }.toSet()
        val remaining = (limit - pinned.size).coerceAtLeast(0)
        if (remaining == 0) {
            return pinned.takeLast(limit)
        }

        val chronologicalCandidates = sorted.filter { it.seq !in pinnedSeqs }
        val maxNotificationInReport = (limit * REPORT_NOTIFICATION_MAX_FRACTION).toInt()
        val nonNotification = chronologicalCandidates.filter { it.category != DiagnosticCategory.NOTIFICATION }
        val notification = chronologicalCandidates.filter { it.category == DiagnosticCategory.NOTIFICATION }

        val selectedNonNotification = nonNotification.takeLast(remaining)
        val notificationSlots = (limit - pinned.size - selectedNonNotification.size)
            .coerceIn(0, maxNotificationInReport)
        val selectedNotification = notification.takeLast(notificationSlots)

        return (pinned + selectedNonNotification + selectedNotification)
            .distinctBy { it.seq }
            .sortedBy { it.seq }
            .takeLast(limit)
    }

    internal fun trimLegacyNotificationNoise(
        events: List<DiagnosticEvent>,
        maxKeep: Int,
    ): List<DiagnosticEvent> {
        if (events.size <= maxKeep) {
            return events.sortedBy { it.seq }
        }
        val sorted = events.sortedBy { it.seq }
        val noiseEvents = sorted.filter { it.name in LEGACY_NOTIFICATION_NOISE_NAMES }
        val otherNotifications = sorted.filter { it.name !in LEGACY_NOTIFICATION_NOISE_NAMES }

        val groupedNoise = noiseEvents.groupBy { event ->
            "${event.name}:${comparableMetadata(event.metadata)}"
        }
        val trimmedNoise = groupedNoise.values.flatMap { group ->
            group.takeLast(2)
        }.sortedBy { it.seq }

        val combined = (otherNotifications + trimmedNoise).sortedBy { it.seq }
        return combined.takeLast(maxKeep)
    }

    private fun buildPinnedEvents(sorted: List<DiagnosticEvent>): List<DiagnosticEvent> {
        val crashEvidence = sorted.filter { it.name in CRASH_EVENT_NAMES }
        val errors = sorted.filter {
            it.category == DiagnosticCategory.ERROR && it.name !in CRASH_EVENT_NAMES
        }.takeLast(ERROR_CAP)
        val permissions = sorted.filter { it.category == DiagnosticCategory.PERMISSION }
            .takeLast(PERMISSION_CAP)
        val users = sorted.filter { it.category == DiagnosticCategory.USER }
            .takeLast(USER_CAP)
        val semanticTrace = sorted.filter { it.name in SEMANTIC_TRACE_EVENT_NAMES }
            .takeLast(SEMANTIC_TRACE_CAP)

        return (crashEvidence + errors + permissions + users + semanticTrace)
            .distinctBy { it.seq }
            .sortedBy { it.seq }
    }

    private fun comparableMetadata(metadata: Map<String, String>): Map<String, String> {
        return metadata.filterKeys { key ->
            key !in setOf("repeat_count", "first_ts_epoch_ms", "last_ts_epoch_ms")
        }
    }

    private const val NOTIFICATION_MAX_FRACTION = 0.50
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
