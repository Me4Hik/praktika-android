// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - ADB command processor
package com.me4hik.praktika.accelerated

import android.content.Context
import android.content.Intent
import android.util.Log
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder

class AcceleratedCommandProcessor(
    private val context: Context,
) {
    private val monotonic = AndroidMonotonicTimeSource(context)

    suspend fun process(command: String, intent: Intent) {
        Log.i(TAG, "command=$command")
        val runtime = PraktikaRuntimeHolder.get(context)
        val clock = runtime.timeProvider as AcceleratedTimeProvider

        when (command) {
            "dump" -> {
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "set_speed" -> {
                val multiplier = intent.getIntExtra("multiplier", -1)
                clock.setSpeedMultiplier(multiplier)
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "pause_clock" -> {
                clock.pauseVirtualClock()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "resume_clock" -> {
                clock.resumeVirtualClock()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "advance_minutes" -> {
                val minutes = intent.getLongExtra("minutes", -1L)
                clock.advanceByVirtualMinutes(minutes)
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "step_event" -> stepEvent(runtime, clock)

            "start_practice" -> {
                runtime.cycleRepository.startPractice()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "reconcile" -> {
                runtime.cycleRepository.reconcile()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "pause_practice" -> {
                runtime.cycleRepository.pausePractice()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "resume_practice" -> {
                runtime.cycleRepository.resumePractice()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            "skip_available" -> {
                runtime.cycleRepository.skipAvailableByUser()
                clock.checkpoint()
                AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
            }

            else -> throw AcceleratedClockCommandException("Unknown command: $command")
        }
    }

    private suspend fun stepEvent(
        runtime: PraktikaRuntime,
        clock: AcceleratedTimeProvider,
    ) {
        val practiceState = runtime.database.practiceStateDao().get()
        if (practiceState == null || !practiceState.isPracticeStarted) {
            throw AcceleratedClockCommandException("Practice has not been started")
        }

        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered()
        if (incomplete.isEmpty()) {
            throw AcceleratedClockCommandException("No incomplete occurrence to step")
        }
        if (incomplete.size > 1) {
            throw AcceleratedClockCorruptionException("Multiple incomplete occurrences")
        }

        val occurrence = incomplete.first()
        val target = when (occurrence.status) {
            QuestionOccurrenceStatus.SCHEDULED -> occurrence.plannedAtEpochMillis
            QuestionOccurrenceStatus.AVAILABLE -> occurrence.availableUntilEpochMillis
            else -> throw AcceleratedClockCommandException("Occurrence is not steppable: ${occurrence.status}")
        }

        val currentVirtual = clock.currentVirtualNow()
        if (target > currentVirtual) {
            clock.advanceToVirtualEpochMillis(target)
        }
        runtime.cycleRepository.reconcile()
        clock.checkpoint()
        AcceleratedDiagnostics.dump(context, runtime, monotonic, clock)
    }

    companion object {
        private const val TAG = "AcceleratedCommand"
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
