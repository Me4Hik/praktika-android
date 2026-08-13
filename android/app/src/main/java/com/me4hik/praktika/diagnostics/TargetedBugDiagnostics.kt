// 10.08.2026 Post-release fixes cursor by Me4Hik START - targeted real-bug trace events
package com.me4hik.praktika.diagnostics

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
    ) {
        recordNotification(
            name = "notification_alarm_fired",
            metadata = buildMap {
                put("occurrence_id", occurrenceId.toString())
                put("alarm_type", alarmType ?: "unknown")
                put("received_at_epoch_ms", receivedAtEpochMs.toString())
            },
        )
    }

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
