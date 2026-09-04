// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik START
package com.me4hik.praktika.accelerated

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.BoundaryEventType
import com.me4hik.praktika.notification.PracticeAlarmIdentity
import com.me4hik.praktika.notification.PracticeAlarmIntents
import com.me4hik.praktika.notification.PracticeAlarmReceiver

/**
 * Accelerated-only harness: derive the current AVAILABLE occurrence with deferredUntil
 * from Room and dispatch one canonical DEFERRED_REMINDER Intent through the real
 * [PracticeAlarmReceiver] via [Context.sendBroadcast].
 *
 * Does not accept occurrence/deferredUntil identity from the ADB command Intent.
 * Does not mutate Room or call AlarmManager.
 * Does not reject based on now vs deferredUntil (future and matured both valid).
 */
object InjectDeferredReminderCommand {
    const val COMMAND = "inject_deferred_reminder"
    const val REQUIRED_PACKAGE = "com.me4hik.praktika.accelerated"

    const val STATUS_VALID_INTENT_DISPATCHED = "VALID_INTENT_DISPATCHED"
    const val STATUS_REJECTED_WRONG_PACKAGE = "REJECTED_WRONG_PACKAGE"
    const val STATUS_REJECTED_NO_OCCURRENCE = "REJECTED_NO_OCCURRENCE"
    const val STATUS_REJECTED_AMBIGUOUS = "REJECTED_AMBIGUOUS"
    const val STATUS_REJECTED_NOT_AVAILABLE = "REJECTED_NOT_AVAILABLE"
    const val STATUS_REJECTED_NO_DEFERRED_UNTIL = "REJECTED_NO_DEFERRED_UNTIL"

    data class IncompleteOccurrence(
        val id: Long,
        val status: QuestionOccurrenceStatus,
        val plannedAtEpochMillis: Long,
        val deferredUntilEpochMillis: Long?,
    )

    data class CommandResult(
        val status: String,
        val wallNowMs: Long,
        val elapsedRealtimeMs: Long,
        val occurrenceId: Long? = null,
        val occurrenceStatusBefore: QuestionOccurrenceStatus? = null,
        val plannedAtMs: Long? = null,
        val deferredUntilMs: Long? = null,
        val intentAction: String? = null,
        val eventType: String? = null,
        val receiverComponent: String? = null,
        val dispatchedIntent: Intent? = null,
    )

    fun evaluate(
        packageName: String,
        wallNowMs: Long,
        elapsedRealtimeMs: Long,
        incomplete: List<IncompleteOccurrence>,
    ): CommandResult {
        if (packageName != REQUIRED_PACKAGE) {
            return CommandResult(
                status = STATUS_REJECTED_WRONG_PACKAGE,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }
        if (incomplete.isEmpty()) {
            return CommandResult(
                status = STATUS_REJECTED_NO_OCCURRENCE,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }
        if (incomplete.size > 1) {
            return CommandResult(
                status = STATUS_REJECTED_AMBIGUOUS,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
            )
        }

        val occurrence = incomplete.single()
        if (occurrence.status != QuestionOccurrenceStatus.AVAILABLE) {
            return CommandResult(
                status = STATUS_REJECTED_NOT_AVAILABLE,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                occurrenceId = occurrence.id,
                occurrenceStatusBefore = occurrence.status,
                plannedAtMs = occurrence.plannedAtEpochMillis,
                deferredUntilMs = occurrence.deferredUntilEpochMillis,
            )
        }
        val deferredUntil = occurrence.deferredUntilEpochMillis
        if (deferredUntil == null) {
            return CommandResult(
                status = STATUS_REJECTED_NO_DEFERRED_UNTIL,
                wallNowMs = wallNowMs,
                elapsedRealtimeMs = elapsedRealtimeMs,
                occurrenceId = occurrence.id,
                occurrenceStatusBefore = occurrence.status,
                plannedAtMs = occurrence.plannedAtEpochMillis,
            )
        }

        val plan = BoundaryAlarmPlan(
            occurrenceId = occurrence.id,
            eventType = BoundaryEventType.DEFERRED_REMINDER,
            triggerAtEpochMillis = deferredUntil,
            plannedAtEpochMillis = occurrence.plannedAtEpochMillis,
        )
        return CommandResult(
            status = STATUS_VALID_INTENT_DISPATCHED,
            wallNowMs = wallNowMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            occurrenceId = occurrence.id,
            occurrenceStatusBefore = occurrence.status,
            plannedAtMs = occurrence.plannedAtEpochMillis,
            deferredUntilMs = deferredUntil,
            intentAction = PracticeAlarmIdentity.actionFor(plan),
            eventType = BoundaryEventType.DEFERRED_REMINDER.name,
            receiverComponent = PracticeAlarmReceiver::class.java.name,
        )
    }

    fun buildCanonicalIntent(context: Context, ready: CommandResult): Intent {
        require(ready.status == STATUS_VALID_INTENT_DISPATCHED) {
            "buildCanonicalIntent requires VALID_INTENT_DISPATCHED"
        }
        val occurrenceId = requireNotNull(ready.occurrenceId)
        val plannedAt = requireNotNull(ready.plannedAtMs)
        val deferredUntil = requireNotNull(ready.deferredUntilMs)
        return PracticeAlarmIntents.buildReceiverIntent(
            context,
            BoundaryAlarmPlan(
                occurrenceId = occurrenceId,
                eventType = BoundaryEventType.DEFERRED_REMINDER,
                triggerAtEpochMillis = deferredUntil,
                plannedAtEpochMillis = plannedAt,
            ),
        )
    }

    /**
     * Evaluate + optionally dispatch. Identity comes only from [incomplete];
     * the ADB command Intent must not supply occurrence/deferredUntil fields here.
     */
    fun execute(
        context: Context,
        incomplete: List<IncompleteOccurrence>,
        packageName: String = context.packageName,
        wallNowMs: Long = System.currentTimeMillis(),
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        sendBroadcast: (Intent) -> Unit = { intent -> context.sendBroadcast(intent) },
    ): CommandResult {
        val evaluated = evaluate(
            packageName = packageName,
            wallNowMs = wallNowMs,
            elapsedRealtimeMs = elapsedRealtimeMs,
            incomplete = incomplete,
        )
        if (evaluated.status != STATUS_VALID_INTENT_DISPATCHED) {
            logResult(evaluated)
            return evaluated
        }
        val intent = buildCanonicalIntent(context, evaluated)
        sendBroadcast(intent)
        val dispatched = evaluated.copy(
            dispatchedIntent = intent,
            receiverComponent = intent.component?.className
                ?: PracticeAlarmReceiver::class.java.name,
        )
        logResult(dispatched)
        return dispatched
    }

    private fun logResult(result: CommandResult) {
        val lines = buildList {
            add("command=$COMMAND")
            add("status=${result.status}")
            add("wall_now_ms=${result.wallNowMs}")
            add("elapsed_realtime_ms=${result.elapsedRealtimeMs}")
            result.occurrenceId?.let { add("occurrence_id=$it") }
            result.occurrenceStatusBefore?.let { add("occurrence_status_before=$it") }
            result.plannedAtMs?.let { add("planned_at_ms=$it") }
            result.deferredUntilMs?.let { add("deferred_until_ms=$it") }
            result.intentAction?.let { add("intent_action=$it") }
            result.eventType?.let { add("event_type=$it") }
            result.receiverComponent?.let { add("receiver_component=$it") }
            add("expected_sync_reason=DEFERRED_ALARM")
        }
        lines.forEach { Log.i(TAG, it) }
    }

    private const val TAG = "AcceleratedCommand"
}
// 04.09.2026 Accelerated deferred-reminder inject harness cursor by Me4Hik END
