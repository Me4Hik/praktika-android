package com.me4hik.praktika.ui.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TourController(
    private val testerToolsStore: TesterToolsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val targetWaitTimeoutMs: Long = 5_000L,
) : ViewModel() {

    private val _session = MutableStateFlow(TourSessionState())
    val session: StateFlow<TourSessionState> = _session.asStateFlow()

    private val _navCommands = MutableSharedFlow<TourNavAction>(extraBufferCapacity = 4)
    val navCommands: SharedFlow<TourNavAction> = _navCommands.asSharedFlow()

    private var targetWaitJob: Job? = null
    private var currentRoute: String? = null

    fun start(definition: TourDefinition = ExperimentalCoreTourV1.definition) {
        targetWaitJob?.cancel()
        val now = clock()
        _session.value = TourSessionState(
            status = TourStatus.Active,
            definitionId = definition.id,
            runId = UUID.randomUUID().toString(),
            stepIndex = 0,
            steps = definition.steps,
            startedAtEpochMs = now,
        )
        onStepEntered(definition.steps.firstOrNull())
    }

    fun onRouteChanged(route: String?) {
        currentRoute = route
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        when (val completion = step.completion) {
            is TourCompletion.RouteMatch -> {
                if (routeEquals(route, completion.prefix)) {
                    completeCurrentStep()
                }
            }
            is TourCompletion.LeftRoute -> {
                if (route != null && !routeInSection(route, completion.prefix)) {
                    completeCurrentStep()
                }
            }
            else -> Unit
        }
    }

    fun onTargetActivated(targetId: TourTargetId) {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (step.completion != TourCompletion.TargetActivation) return
        if (step.targetId != null && step.targetId != targetId) return
        completeCurrentStep()
    }

    fun onNext() {
        val step = _session.value.currentStep ?: return
        if (step.completion != TourCompletion.ManualAdvance) return
        if (step.id == TourStepId.FINISHED) {
            finishCompleted(extraCompleted = listOf(TourStepId.FINISHED))
        } else {
            completeCurrentStep()
        }
    }

    fun onSkip() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        targetWaitJob?.cancel()
        val skipNav = step.onSkipNav
        val nextIndex = state.stepIndex + 1
        if (nextIndex >= state.steps.size) {
            finishCompleted(
                extraSkipped = listOf(step.id),
            )
            return
        }
        // Advance first so RouteMatch of the skipped step cannot fire on prep navigate.
        _session.update {
            it.copy(
                stepIndex = nextIndex,
                skippedStepIds = it.skippedStepIds + step.id,
                waitingForTarget = false,
            )
        }
        onStepEntered(_session.value.currentStep)
        if (skipNav != TourNavAction.NONE) {
            _navCommands.tryEmit(skipNav)
        }
    }

    fun onExit(reasonExitStep: TourStepId? = _session.value.currentStep?.id) {
        val state = _session.value
        if (!state.isActive) return
        targetWaitJob?.cancel()
        val ended = clock()
        val result = TourRunResult(
            runId = state.runId ?: UUID.randomUUID().toString(),
            startedAtEpochMs = state.startedAtEpochMs ?: ended,
            endedAtEpochMs = ended,
            completedStepIds = state.completedStepIds.map { it.name },
            skippedStepIds = state.skippedStepIds.map { it.name },
            exitStepId = reasonExitStep?.name,
            completedTour = false,
        )
        _session.value = state.copy(
            status = TourStatus.Exited,
            exitStepId = reasonExitStep,
            endedAtEpochMs = ended,
            waitingForTarget = false,
        )
        viewModelScope.launch {
            testerToolsStore.saveTourResult(result)
        }
    }

    fun notifyTargetAvailable(targetId: TourTargetId) {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (step.targetId != targetId) return
        if (state.waitingForTarget) {
            targetWaitJob?.cancel()
            _session.update { it.copy(waitingForTarget = false) }
        }
    }

    /**
     * Immediate skip of the current step (no target-wait timeout).
     * Used for sound-library all-hidden fallback.
     *
     * When [expectedStepId] is set, skips only if that step is still current
     * (guards against a superseded LaunchedEffect racing after PREVIEW→SELECT→HIDE).
     */
    fun skipStepImmediately(reason: String, expectedStepId: TourStepId? = null) {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (expectedStepId != null && step.id != expectedStepId) return
        skipCurrentWithReason(step, reason)
    }

    fun setTipOverrideResId(resId: Int?) {
        if (!_session.value.isActive) return
        _session.update { it.copy(tipOverrideResId = resId) }
    }

    private fun onStepEntered(step: TourStep?) {
        if (step == null) {
            finishCompleted()
            return
        }
        _session.update { it.copy(tipOverrideResId = null) }
        if (step.onEnterNav != TourNavAction.NONE) {
            _navCommands.tryEmit(step.onEnterNav)
        }
        if (step.targetId != null) {
            _session.update { it.copy(waitingForTarget = true) }
            targetWaitJob?.cancel()
            targetWaitJob = viewModelScope.launch {
                delay(targetWaitTimeoutMs)
                val latest = _session.value
                if (latest.isActive &&
                    latest.currentStep?.id == step.id &&
                    latest.waitingForTarget
                ) {
                    skipDueToUnavailableTarget(step.id)
                }
            }
        } else {
            _session.update { it.copy(waitingForTarget = false) }
        }
        currentRoute?.let { onRouteChanged(it) }
    }

    private fun skipDueToUnavailableTarget(stepId: TourStepId) {
        val state = _session.value
        if (!state.isActive || state.currentStep?.id != stepId) return
        val step = state.currentStep ?: return
        skipCurrentWithReason(step, TourSkipReasons.TARGET_UNAVAILABLE)
    }

    private fun skipCurrentWithReason(step: TourStep, reason: String) {
        val state = _session.value
        if (!state.isActive || state.currentStep?.id != step.id) return
        targetWaitJob?.cancel()
        val skipNav = step.onSkipNav
        val nextIndex = state.stepIndex + 1
        if (nextIndex >= state.steps.size) {
            finishCompleted(extraSkipped = listOf(step.id), reason = reason)
            return
        }
        _session.update {
            it.copy(
                stepIndex = nextIndex,
                skippedStepIds = it.skippedStepIds + step.id,
                skipReasons = it.skipReasons + (step.id to reason),
                waitingForTarget = false,
                tipOverrideResId = null,
            )
        }
        onStepEntered(_session.value.currentStep)
        if (skipNav != TourNavAction.NONE) {
            _navCommands.tryEmit(skipNav)
        }
    }

    private fun completeCurrentStep() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        targetWaitJob?.cancel()
        val nextIndex = state.stepIndex + 1
        if (nextIndex >= state.steps.size) {
            finishCompleted(extraCompleted = listOf(step.id))
            return
        }
        _session.update {
            it.copy(
                stepIndex = nextIndex,
                completedStepIds = it.completedStepIds + step.id,
                waitingForTarget = false,
            )
        }
        onStepEntered(_session.value.currentStep)
    }

    private fun finishCompleted(
        extraCompleted: List<TourStepId> = emptyList(),
        extraSkipped: List<TourStepId> = emptyList(),
        reason: String? = null,
    ) {
        targetWaitJob?.cancel()
        val state = _session.value
        val ended = clock()
        val completed = state.completedStepIds + extraCompleted
        val skipped = state.skippedStepIds + extraSkipped
        val skipReasons = if (reason != null && extraSkipped.isNotEmpty()) {
            state.skipReasons + extraSkipped.associateWith { reason }
        } else {
            state.skipReasons
        }
        val result = TourRunResult(
            runId = state.runId ?: UUID.randomUUID().toString(),
            startedAtEpochMs = state.startedAtEpochMs ?: ended,
            endedAtEpochMs = ended,
            completedStepIds = completed.map { it.name },
            skippedStepIds = skipped.map { it.name },
            exitStepId = null,
            completedTour = true,
        )
        _session.value = state.copy(
            status = TourStatus.Completed,
            completedStepIds = completed,
            skippedStepIds = skipped,
            skipReasons = skipReasons,
            endedAtEpochMs = ended,
            waitingForTarget = false,
            stepIndex = state.steps.lastIndex.coerceAtLeast(0),
        )
        viewModelScope.launch {
            testerToolsStore.saveTourResult(result)
        }
    }

    companion object {
        /** Exact route equality for RouteMatch completions. */
        fun routeEquals(route: String?, expected: String): Boolean = route == expected

        /** Still inside a section (hub or nested). */
        fun routeInSection(route: String?, prefix: String): Boolean {
            if (route == null) return false
            return route == prefix || route.startsWith("$prefix/")
        }
    }
}

class TourControllerFactory(
    private val testerToolsStore: TesterToolsStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TourController::class.java)) {
            return TourController(testerToolsStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
