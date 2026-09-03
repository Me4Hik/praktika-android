// 10.08.2026 Post-release fixes cursor by Me4Hik START - targeted real-bug trace events
package com.me4hik.praktika.diagnostics

import com.me4hik.praktika.notification.PlannedBoundarySchedulerDecision

object TargetedBugDiagnostics {
    internal val SEMANTIC_TRACE_EVENT_NAMES = setOf(
        "answer_save_attempt",
        "answer_save_result",
        "schedule_changed",
        "schedule_change_result",
        "notification_alarm_scheduled",
        "notification_alarm_cancelled",
        "notification_alarm_fired",
        "notification_post_attempt",
        "notification_post_result",
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        "notification_scheduler_decision",
        "malformed_alarm_intent",
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        // 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik START - retain SYSTEM_EVENT_RECEIVED
        "system_event_received",
        // 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik END
        // 03.09.2026 Case2 minimal observability cursor by Me4Hik START - planned alarm abort markers
        "planned_alarm_init_failed",
        "planned_alarm_receiver_failed",
        // 03.09.2026 Case2 minimal observability cursor by Me4Hik END
        // 03.09.2026 Case2 init internal observability cursor by Me4Hik START - runtime init failure reason
        "runtime_init_failed",
        // 03.09.2026 Case2 init internal observability cursor by Me4Hik END
    )

    object NotificationTraceContext {
        var syncReason: String? = null
        var deliveryCapability: String? = null
    }

    fun recordAnswerSaveAttempt(occurrenceId: Long, draftBlank: Boolean) {
        recordUser(
            name = "answer_save_attempt",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("route", currentRoute())
                put("draft_blank", draftBlank.toString())
            },
        )
    }

    fun recordAnswerSaveResult(
        occurrenceId: Long,
        result: String,
        reasonEnum: String,
    ) {
        recordUser(
            name = "answer_save_result",
            metadata = mapOf(
                "occurrence_id" to occurrenceId.toString(),
                "result" to result,
                "reason_enum" to reasonEnum,
                "route" to currentRoute(),
            ),
        )
    }

    fun recordAnswerSaveFailed(occurrenceId: Long, exception: Throwable) {
        if (DiagnosticsRecorder.isInitialized()) {
            DiagnosticsRecorder.get().recordCaughtException("AnswerViewModel.saveAnswer", exception)
        }
        recordAnswerSaveResult(
            occurrenceId = occurrenceId,
            result = "failed",
            reasonEnum = exception.javaClass.simpleName,
        )
    }

    fun recordScheduleChanged(
        slotMinutesBefore: List<Int>,
        slotMinutesAfter: List<Int>,
        occurrenceId: Long?,
        occurrenceStatus: String?,
    ) {
        recordApp(
            name = "schedule_changed",
            metadata = buildMap {
                put("slot_minutes_before", formatSlotMinutes(slotMinutesBefore))
                put("slot_minutes_after", formatSlotMinutes(slotMinutesAfter))
                put("active_occurrence_id", occurrenceId?.toString() ?: "")
                put("active_occurrence_status", occurrenceStatus ?: "")
            },
        )
    }

    fun recordScheduleChangeResult(result: String, detail: String = "") {
        recordApp(
            name = "schedule_change_result",
            metadata = buildMap {
                put("result", result)
                if (detail.isNotEmpty()) {
                    put("detail", detail)
                }
            },
        )
    }

    fun recordAlarmScheduled(
        occurrenceId: Long,
        alarmType: String,
        triggerAtEpochMs: Long,
        windowMs: Long,
        schedulerApi: String = "setWindow",
        exactAlarmCapability: String = "",
        reasonFromSync: String?,
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        semanticPlannedAtEpochMs: Long? = null,
        schedulerDecision: String? = null,
        wallClockMs: Long? = null,
        elapsedRealtimeMs: Long? = null,
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    ) {
        recordNotification(
            name = "notification_alarm_scheduled",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("alarm_type", alarmType)
                put("trigger_at_epoch_ms", triggerAtEpochMs.toString())
                put("window_ms", windowMs.toString())
                put("scheduler_api", schedulerApi)
                put("exact_alarm_capability", exactAlarmCapability)
                put("reason_from_sync", reasonFromSync ?: "")
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
                semanticPlannedAtEpochMs?.let { put("semantic_planned_at_epoch_ms", it.toString()) }
                schedulerDecision?.let { put("scheduler_decision", it) }
                wallClockMs?.let { put("wall_clock_ms", it.toString()) }
                elapsedRealtimeMs?.let { put("elapsed_realtime_ms", it.toString()) }
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
            },
        )
    }

    fun recordAlarmCancelled(
        occurrenceId: Long?,
        reason: String,
    ) {
        recordNotification(
            name = "notification_alarm_cancelled",
            metadata = buildMap {
                if (occurrenceId != null && occurrenceId > 0L) {
                    put("occurrence_id", occurrenceId.toString())
                }
                put("reason", reason)
            },
        )
    }

    fun recordAlarmFired(
        occurrenceId: Long,
        alarmType: String?,
        receivedAtEpochMs: Long,
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
        receiverEventType: String? = null,
        semanticPlannedAtEpochMs: Long? = null,
        triggerAtEpochMs: Long? = null,
        intentFlags: Int? = null,
        wallClockMs: Long? = null,
        elapsedRealtimeMs: Long? = null,
        deltaToPlannedMs: Long? = null,
        logicalPiIdentity: String? = null,
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    ) {
        recordNotification(
            name = "notification_alarm_fired",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("alarm_type", alarmType ?: "unknown")
                put("received_at_epoch_ms", receivedAtEpochMs.toString())
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
                receiverEventType?.let { put("receiver_event_type", it) }
                semanticPlannedAtEpochMs?.let { put("receiver_semantic_planned_at", it.toString()) }
                triggerAtEpochMs?.let { put("receiver_trigger_at", it.toString()) }
                intentFlags?.let { put("receiver_intent_flags", it.toString()) }
                wallClockMs?.let { put("receiver_wall_clock_ms", it.toString()) }
                elapsedRealtimeMs?.let { put("receiver_elapsed_realtime_ms", it.toString()) }
                deltaToPlannedMs?.let { put("delta_to_planned_ms", it.toString()) }
                logicalPiIdentity?.let { put("logical_pi_identity", it) }
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
            },
        )
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
    data class PowerDiagnosticsSnapshot(
        val deviceIdleMode: Boolean? = null,
        val powerSaveMode: Boolean? = null,
        val ignoringBatteryOptimizations: Boolean? = null,
    )

    fun recordSchedulerDecision(
        decision: String,
        occurrenceId: Long,
        eventType: String?,
        semanticPlannedAtEpochMs: Long,
        triggerAtEpochMs: Long,
        schedulerApi: String? = null,
        exactAlarmCapability: String? = null,
        powerSnapshot: PowerDiagnosticsSnapshot? = null,
    ) {
        recordNotification(
            name = "notification_scheduler_decision",
            metadata = buildMap {
                put("scheduler_decision", decision)
                put("occurrence_id", occurrenceId.toString())
                put("receiver_event_type", eventType ?: "")
                put("semantic_planned_at_epoch_ms", semanticPlannedAtEpochMs.toString())
                put("trigger_at_epoch_ms", triggerAtEpochMs.toString())
                put("wall_clock_ms", System.currentTimeMillis().toString())
                put("elapsed_realtime_ms", android.os.SystemClock.elapsedRealtime().toString())
                schedulerApi?.let { put("scheduler_api", it) }
                exactAlarmCapability?.let { put("exact_alarm_capability", it) }
                powerSnapshot?.deviceIdleMode?.let { put("device_idle_mode", it.toString()) }
                powerSnapshot?.powerSaveMode?.let { put("power_save_mode", it.toString()) }
                powerSnapshot?.ignoringBatteryOptimizations?.let {
                    put("ignoring_battery_optimizations", it.toString())
                }
            },
        )
    }

    fun recordMalformedAlarmIntent(
        occurrenceId: Long,
        eventType: String?,
        triggerAtEpochMs: Long,
        semanticPlannedAtEpochMs: Long,
    ) {
        recordNotification(
            name = "malformed_alarm_intent",
            metadata = mapOf(
                "occurrence_id" to occurrenceId.toString(),
                "receiver_event_type" to (eventType ?: ""),
                "trigger_at_epoch_ms" to triggerAtEpochMs.toString(),
                "semantic_planned_at_epoch_ms" to semanticPlannedAtEpochMs.toString(),
                "scheduler_decision" to PlannedBoundarySchedulerDecision.IGNORE_MALFORMED.name,
            ),
        )
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    fun recordNotificationPostAttempt(
        occurrenceId: Long,
        channelId: String,
        capability: String?,
    ) {
        recordNotification(
            name = "notification_post_attempt",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("channel_id", channelId)
                put("capability", capability ?: "")
            },
        )
    }

    fun recordNotificationPostResult(
        occurrenceId: Long,
        result: String,
        exceptionClass: String? = null,
    ) {
        recordNotification(
            name = "notification_post_result",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("result", result)
                if (!exceptionClass.isNullOrEmpty()) {
                    put("exception_class", exceptionClass)
                }
                // 10.08.2026 Post-release fixes cursor by Me4Hik START - notification planned boundary trigger bias/recovery
                put("wall_clock_ms", System.currentTimeMillis().toString())
                put("elapsed_realtime_ms", android.os.SystemClock.elapsedRealtime().toString())
                // 10.08.2026 Post-release fixes cursor by Me4Hik END
            },
        )
    }

    fun recordAppBackgrounded() {
        recordApp(
            name = "app_backgrounded",
            metadata = mapOf("current_route" to currentRoute()),
        )
    }

    fun recordAppForegrounded() {
        recordApp(
            name = "app_foregrounded",
            metadata = mapOf("current_route" to currentRoute()),
        )
    }

    fun recordExactAlarmCapabilityState(
        capability: String,
        apiLevel: Int,
        source: String,
    ) {
        recordNotification(
            name = "exact_alarm_capability_state",
            metadata = mapOf(
                "capability" to capability,
                "api_level" to apiLevel.toString(),
                "source" to source,
            ),
        )
    }

    fun recordExactAlarmSettingsCta(source: String) {
        recordNotification(
            name = "exact_alarm_settings_cta",
            metadata = mapOf("source" to source),
        )
    }

    // 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik START - SYSTEM_EVENT_RECEIVED
    fun recordSystemEventReceived(
        action: String,
        syncReason: String,
        wallClockMs: Long,
        elapsedRealtimeMs: Long,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        // Persist synchronously so a short-lived BOOT/system-event process retains the marker.
        DiagnosticsRecorder.get().recordSync(
            category = DiagnosticCategory.NOTIFICATION,
            name = "system_event_received",
            metadata = mapOf(
                "action" to action,
                "sync_reason" to syncReason,
                "wall_clock_ms" to wallClockMs.toString(),
                "elapsed_realtime_ms" to elapsedRealtimeMs.toString(),
            ),
        )
    }
    // 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik END

    // 03.09.2026 Case2 minimal observability cursor by Me4Hik START - planned alarm abort markers
    fun recordPlannedAlarmInitFailed(
        occurrenceId: Long,
        receiverEventType: String?,
        semanticPlannedAtEpochMs: Long,
        triggerAtEpochMs: Long,
        wallClockMs: Long,
        elapsedRealtimeMs: Long,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        // Sync write: alarm cold-start process may die immediately after abort.
        DiagnosticsRecorder.get().recordSync(
            category = DiagnosticCategory.NOTIFICATION,
            name = "planned_alarm_init_failed",
            metadata = mapOf(
                "occurrence_id" to occurrenceId.toString(),
                "receiver_event_type" to (receiverEventType ?: ""),
                "semantic_planned_at_epoch_ms" to semanticPlannedAtEpochMs.toString(),
                "trigger_at_epoch_ms" to triggerAtEpochMs.toString(),
                "failure_stage" to "ensureInitialized",
                "wall_clock_ms" to wallClockMs.toString(),
                "elapsed_realtime_ms" to elapsedRealtimeMs.toString(),
            ),
        )
    }

    fun recordPlannedAlarmReceiverFailed(
        occurrenceId: Long,
        receiverEventType: String?,
        exceptionClass: String,
        wallClockMs: Long,
        elapsedRealtimeMs: Long,
        semanticPlannedAtEpochMs: Long? = null,
        triggerAtEpochMs: Long? = null,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().recordSync(
            category = DiagnosticCategory.NOTIFICATION,
            name = "planned_alarm_receiver_failed",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("receiver_event_type", receiverEventType ?: "")
                put("exception_class", exceptionClass)
                put("wall_clock_ms", wallClockMs.toString())
                put("elapsed_realtime_ms", elapsedRealtimeMs.toString())
                semanticPlannedAtEpochMs?.let { put("semantic_planned_at_epoch_ms", it.toString()) }
                triggerAtEpochMs?.let { put("trigger_at_epoch_ms", it.toString()) }
            },
        )
    }
    // 03.09.2026 Case2 minimal observability cursor by Me4Hik END

    // 03.09.2026 Case2 init internal observability cursor by Me4Hik START - runtime init failure reason
    fun recordRuntimeInitFailed(
        failureStage: String,
        exceptionClass: String,
        wallClockMs: Long,
        elapsedRealtimeMs: Long,
    ) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        // Sync write: headless cold-start process may die immediately after sticky false.
        DiagnosticsRecorder.get().recordSync(
            category = DiagnosticCategory.NOTIFICATION,
            name = "runtime_init_failed",
            metadata = mapOf(
                "failure_stage" to failureStage,
                "exception_class" to exceptionClass,
                "wall_clock_ms" to wallClockMs.toString(),
                "elapsed_realtime_ms" to elapsedRealtimeMs.toString(),
            ),
        )
    }
    // 03.09.2026 Case2 init internal observability cursor by Me4Hik END

    fun extractOccurrenceId(route: String): String? {
        return when {
            route.startsWith("question/") -> route.removePrefix("question/").toLongOrNull()?.toString()
            route.startsWith("answer/") -> route.removePrefix("answer/").toLongOrNull()?.toString()
            else -> null
        }
    }

    fun screenOpenMetadata(route: String, previousRoute: String?): Map<String, String> {
        return buildMap {
            put("route", route)
            put("previous_route", previousRoute ?: "")
            extractOccurrenceId(route)?.let { put("occurrence_id", it) }
        }
    }

    internal fun formatSlotMinutes(minutes: List<Int>): String = minutes.joinToString(",")

    private fun currentRoute(): String = NavigationRouteTracker.currentRoute ?: "unknown"

    private fun recordUser(name: String, metadata: Map<String, String>) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.USER,
            name = name,
            metadata = metadata,
        )
    }

    private fun recordApp(name: String, metadata: Map<String, String>) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.APP,
            name = name,
            metadata = metadata,
        )
    }

    private fun recordNotification(name: String, metadata: Map<String, String>) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.NOTIFICATION,
            name = name,
            metadata = metadata,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
