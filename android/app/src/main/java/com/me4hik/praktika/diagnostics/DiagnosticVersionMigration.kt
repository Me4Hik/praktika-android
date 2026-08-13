// 10.08.2026 Post-release fixes cursor by Me4Hik START - diagnostics buffer retention
package com.me4hik.praktika.diagnostics

internal object DiagnosticVersionMigration {
    fun shouldApplyVersionBoundary(
        recordedVersionCode: Int?,
        currentVersionCode: Int,
        existingEvents: List<DiagnosticEvent>,
    ): Boolean {
        if (recordedVersionCode == currentVersionCode) {
            return false
        }
        if (recordedVersionCode != null) {
            return true
        }
        return existingEvents.isNotEmpty()
    }

    fun migrateIfNeeded(
        store: DiagnosticEventStore,
        recordedVersionCode: Int?,
        currentVersionCode: Int,
        nowEpochMs: Long,
        nowMonoMs: Long,
    ): Boolean {
        val existing = store.readAll()
        if (!shouldApplyVersionBoundary(recordedVersionCode, currentVersionCode, existing)) {
            return false
        }
        val cleaned = DiagnosticEventRetentionPolicy.applyVersionBoundary(
            events = existing,
            currentVersionCode = currentVersionCode,
            maxEvents = DiagnosticEventStore.MAX_EVENTS,
        )
        store.replaceAll(cleaned)
        store.appendSync(
            DiagnosticEventRetentionPolicy.createVersionBoundaryEvent(
                seq = store.nextSeq(),
                tsEpochMs = nowEpochMs,
                monoMs = nowMonoMs,
                previousVersionCode = recordedVersionCode,
                currentVersionCode = currentVersionCode,
            ),
        )
        return true
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
