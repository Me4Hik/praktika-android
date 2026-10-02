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

    private val _navCommands = MutableSharedFlow<TourNavCommand>(extraBufferCapacity = 16)
    val navCommands: SharedFlow<TourNavCommand> = _navCommands.asSharedFlow()

    private var targetWaitJob: Job? = null
    private var currentRoute: String? = null

    fun start(definition: TourDefinition = ExperimentalCoreTourV1.definition) {
        targetWaitJob?.cancel()
        val now = clock()
        val outcomes = definition.actionTaskOrder.associateWith { TourTaskOutcome.Pending }
        _session.value = TourSessionState(
            status = TourStatus.Active,
            definitionId = definition.id,
            runId = UUID.randomUUID().toString(),
            stepIndex = 0,
            steps = definition.steps,
            startedAtEpochMs = now,
            coreCompleted = false,
            actionReady = false,
            navGeneration = 1L,
            taskOutcomes = outcomes,
        )
        enterStepDisarmed(definition.steps.firstOrNull())
    }

    fun onRouteChanged(route: String?) {
        currentRoute = route
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return

        if (!state.actionReady) {
            maybeArmAfterRoute(step, route)
            return
        }

        when (val completion = step.completion) {
            is TourCompletion.RouteMatch -> {
                // RouteMatch is the *result* of a click that leaves expectedRoutePrefix.
                // Do not require still being on expectedRoutePrefix.
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
        if (!state.isActive || !state.actionReady) return
        val step = state.currentStep ?: return
        if (step.completion != TourCompletion.TargetActivation) return
        if (step.expectedRoutePrefix != null &&
            !TourNavPlanner.routeMatchesExpected(currentRoute, step.expectedRoutePrefix)
        ) {
            return
        }
        if (step.targetId != null && !targetsMatch(step.targetId, targetId)) return
        completeCurrentStep()
    }

    fun onNext() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (step.completion != TourCompletion.ManualAdvance) return
        val nextIndex = state.stepIndex + 1
        if (nextIndex >= state.steps.size) {
            finishCompleted(extraCompleted = listOf(step.id))
        } else {
            completeCurrentStep()
        }
    }

    /** Explicit user Skip — whole current semantic task. */
    fun onSkip() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (step.type == TourStepType.GATE) return
        targetWaitJob?.cancel()

        val skipSpan = remainingTaskSpan(state)
        val taskId = step.taskId
        val skipNavRaw = step.onSkipNav
        val nextIndex = state.stepIndex + skipSpan.size
        val generation = state.navGeneration + 1

        var outcomes = state.taskOutcomes
        if (taskId != null) {
            outcomes = outcomes + (taskId to TourTaskOutcome.UserSkipped)
        }

        if (nextIndex >= state.steps.size) {
            emitExpandedNav(skipNavRaw, generation)
            finishCompleted(
                extraSkipped = skipSpan.map { it.id },
                reason = TourSkipReasons.USER_SKIP,
                taskOutcomes = outcomes,
            )
            return
        }

        val nextStep = state.steps[nextIndex]
        if (nextStep.type == TourStepType.GATE && !allTerminal(outcomes, state.definition)) {
            // Should not reach Gate without all tasks terminal — stay after marking skip.
            _session.update {
                it.copy(
                    skippedStepIds = it.skippedStepIds + skipSpan.map { s -> s.id },
                    skipReasons = it.skipReasons +
                        skipSpan.associate { s -> s.id to TourSkipReasons.USER_SKIP },
                    waitingForTarget = false,
                    tipOverrideResId = null,
                    actionReady = false,
                    navGeneration = generation,
                    taskOutcomes = outcomes,
                )
            }
            emitExpandedNav(skipNavRaw, generation)
            return
        }

        _session.update {
            it.copy(
                stepIndex = nextIndex,
                skippedStepIds = it.skippedStepIds + skipSpan.map { s -> s.id },
                skipReasons = it.skipReasons +
                    skipSpan.associate { s -> s.id to TourSkipReasons.USER_SKIP },
                waitingForTarget = false,
                tipOverrideResId = null,
                actionReady = false,
                navGeneration = generation,
                taskOutcomes = outcomes,
            )
        }
        // Cleanup nav first, then arm next when its canonical route settles.
        emitExpandedNav(skipNavRaw, generation)
        emitEnterNav(nextStep, generation)
        maybeArmAfterRoute(nextStep, currentRoute)
        maybeEnterGateMilestone(nextStep, outcomes)
    }

    fun onGateContinueOptional() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (step.completion != TourCompletion.GateChoice) return
        if (!state.allActionTasksTerminal()) return
        completeCurrentStep()
    }

    /**
     * Gate «Закончить»: skip optional overview and open the shared FINAL completion screen.
     * Core milestone is already persisted on Gate entry — do not finish the tour here.
     */
    fun onGateFinish() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (step.completion != TourCompletion.GateChoice) return
        if (!state.allActionTasksTerminal()) return

        val completionIndex = state.steps.indexOfFirst { it.uiPhase() == TourUiPhase.COMPLETION }
        if (completionIndex < 0) {
            // Definitions without FINAL keep legacy immediate-complete behavior.
            finishCompleted(extraCompleted = listOf(step.id))
            return
        }

        targetWaitJob?.cancel()
        val generation = state.navGeneration + 1
        val nextStep = state.steps[completionIndex]
        _session.update {
            it.copy(
                stepIndex = completionIndex,
                completedStepIds = it.completedStepIds + step.id,
                waitingForTarget = false,
                tipOverrideResId = null,
                actionReady = false,
                navGeneration = generation,
            )
        }
        emitEnterNav(nextStep, generation)
        maybeArmAfterRoute(nextStep, currentRoute)
    }

    fun onFinishTour() {
        val state = _session.value
        if (!state.isActive) return
        finishCompleted()
    }

    fun onExit(reasonExitStep: TourStepId? = _session.value.currentStep?.id) {
        val state = _session.value
        if (!state.isActive) return
        targetWaitJob?.cancel()
        val ended = clock()
        val completedTour = state.coreCompleted
        val result = TourRunResult(
            runId = state.runId ?: UUID.randomUUID().toString(),
            startedAtEpochMs = state.startedAtEpochMs ?: ended,
            endedAtEpochMs = ended,
            completedStepIds = state.completedStepIds.map { it.name },
            skippedStepIds = state.skippedStepIds.map { it.name },
            exitStepId = reasonExitStep?.name,
            completedTour = completedTour,
        )
        _session.value = state.copy(
            status = TourStatus.Exited,
            exitStepId = reasonExitStep,
            endedAtEpochMs = ended,
            waitingForTarget = false,
            actionReady = false,
        )
        viewModelScope.launch {
            testerToolsStore.saveTourResult(result)
        }
    }

    fun notifyTargetAvailable(targetId: TourTargetId) {
        val state = _session.value
        if (!state.isActive || !state.actionReady) return
        val step = state.currentStep ?: return
        val matches = step.targetId == targetId ||
            (step.targetId == TourTargetId.SOUND_LIBRARY_ANY_ITEM &&
                targetId == TourTargetId.SOUND_LIBRARY_ANY_ITEM)
        if (!matches) return
        if (state.waitingForTarget) {
            targetWaitJob?.cancel()
            _session.update { it.copy(waitingForTarget = false) }
        }
    }

    /**
     * Soft recovery for sound-library all-hidden.
     * - Steps with [TourStep.taskId] (ACTION CORE): stay on step; user must explicit Skip.
     * - Legacy steps without taskId: advance one internal step only (not whole-task / not UserSkip).
     */
    fun skipStepImmediately(reason: String, expectedStepId: TourStepId? = null) {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        if (expectedStepId != null && step.id != expectedStepId) return
        targetWaitJob?.cancel()
        _session.update {
            it.copy(
                waitingForTarget = false,
                skipReasons = it.skipReasons + (step.id to reason),
            )
        }
        if (reason != TourSkipReasons.NO_VISIBLE_BUILTIN) return
        if (step.taskId != null) return
        softAdvanceOneStep(step, reason)
    }

    private fun softAdvanceOneStep(step: TourStep, reason: String) {
        val state = _session.value
        if (!state.isActive || state.currentStep?.id != step.id) return
        val nextIndex = state.stepIndex + 1
        val generation = state.navGeneration + 1
        if (nextIndex >= state.steps.size) return
        _session.update {
            it.copy(
                stepIndex = nextIndex,
                skippedStepIds = it.skippedStepIds + step.id,
                skipReasons = it.skipReasons + (step.id to reason),
                waitingForTarget = false,
                tipOverrideResId = null,
                actionReady = false,
                navGeneration = generation,
            )
        }
        enterStepDisarmed(_session.value.currentStep)
    }

    fun setTipOverrideResId(resId: Int?) {
        if (!_session.value.isActive) return
        _session.update { it.copy(tipOverrideResId = resId) }
    }

    private fun enterStepDisarmed(step: TourStep?) {
        if (step == null) {
            finishCompleted()
            return
        }
        val generation = _session.value.navGeneration
        _session.update {
            it.copy(
                tipOverrideResId = null,
                waitingForTarget = false,
                actionReady = false,
            )
        }
        emitEnterNav(step, generation)
        maybeArmAfterRoute(step, currentRoute)
        maybeEnterGateMilestone(step, _session.value.taskOutcomes)
    }

    private fun maybeArmAfterRoute(step: TourStep, route: String?) {
        val state = _session.value
        if (!state.isActive || state.currentStep?.id != step.id) return
        if (state.actionReady) return

        if (step.type == TourStepType.GATE) {
            if (!state.allActionTasksTerminal()) return
            _session.update { it.copy(actionReady = true, waitingForTarget = false) }
            return
        }

        if (step.expectedRoutePrefix != null &&
            !TourNavPlanner.routeMatchesExpected(route, step.expectedRoutePrefix)
        ) {
            return
        }

        armStep(step)
    }

    private fun armStep(step: TourStep) {
        val state = _session.value
        if (!state.isActive || state.currentStep?.id != step.id) return
        _session.update { it.copy(actionReady = true, tipOverrideResId = null) }

        if (step.targetId != null && step.requiresActionCue()) {
            _session.update { it.copy(waitingForTarget = true) }
            targetWaitJob?.cancel()
            targetWaitJob = viewModelScope.launch {
                delay(targetWaitTimeoutMs)
                val latest = _session.value
                if (latest.isActive &&
                    latest.currentStep?.id == step.id &&
                    latest.actionReady &&
                    latest.waitingForTarget
                ) {
                    onTechnicalTargetTimeout(step.id)
                }
            }
        } else {
            _session.update { it.copy(waitingForTarget = false) }
        }

        // Completions only after arm — evaluate fresh route, never stale pre-arm.
        if (step.completion is TourCompletion.RouteMatch ||
            step.completion is TourCompletion.LeftRoute
        ) {
            currentRoute?.let { evaluateArmedRouteCompletion(step, it) }
        }
    }

    private fun evaluateArmedRouteCompletion(step: TourStep, route: String) {
        val state = _session.value
        if (!state.isActive || !state.actionReady || state.currentStep?.id != step.id) return
        when (val completion = step.completion) {
            is TourCompletion.RouteMatch -> {
                if (routeEquals(route, completion.prefix)) {
                    completeCurrentStep()
                }
            }
            is TourCompletion.LeftRoute -> {
                if (!routeInSection(route, completion.prefix)) {
                    completeCurrentStep()
                }
            }
            else -> Unit
        }
    }

    /** Technical timeout: stay on task, no skipNav, no advance, no Gate. */
    private fun onTechnicalTargetTimeout(stepId: TourStepId) {
        val state = _session.value
        if (!state.isActive || state.currentStep?.id != stepId) return
        targetWaitJob?.cancel()
        _session.update {
            it.copy(
                waitingForTarget = false,
                skipReasons = it.skipReasons + (stepId to TourSkipReasons.TARGET_UNAVAILABLE),
            )
        }
    }

    private fun remainingTaskSpan(state: TourSessionState): List<TourStep> {
        val current = state.currentStep ?: return emptyList()
        val taskId = current.taskId
        if (taskId == null) return listOf(current)
        val span = mutableListOf<TourStep>()
        for (i in state.stepIndex until state.steps.size) {
            val s = state.steps[i]
            if (s.taskId != taskId) break
            span += s
        }
        return span
    }

    private fun completeCurrentStep() {
        val state = _session.value
        if (!state.isActive) return
        val step = state.currentStep ?: return
        targetWaitJob?.cancel()
        val completeNav = step.onCompleteNav
        val nextIndex = state.stepIndex + 1
        val generation = state.navGeneration + 1

        var outcomes = state.taskOutcomes
        val taskId = step.taskId
        if (taskId != null) {
            val remainingSame = state.steps
                .drop(nextIndex)
                .takeWhile { it.taskId == taskId }
            if (remainingSame.isEmpty()) {
                outcomes = outcomes + (taskId to TourTaskOutcome.Completed)
            }
        }

        if (nextIndex >= state.steps.size) {
            emitExpandedNav(completeNav, generation)
            finishCompleted(
                extraCompleted = listOf(step.id),
                taskOutcomes = outcomes,
            )
            return
        }

        val nextStep = state.steps[nextIndex]
        if (nextStep.type == TourStepType.GATE && !allTerminal(outcomes, state.definition)) {
            _session.update {
                it.copy(
                    completedStepIds = it.completedStepIds + step.id,
                    waitingForTarget = false,
                    tipOverrideResId = null,
                    actionReady = true,
                    taskOutcomes = outcomes,
                    navGeneration = generation,
                )
            }
            emitExpandedNav(completeNav, generation)
            return
        }

        val enteringGate = nextStep.type == TourStepType.GATE && allTerminal(outcomes, state.definition)

        _session.update {
            it.copy(
                stepIndex = nextIndex,
                completedStepIds = it.completedStepIds + step.id,
                waitingForTarget = false,
                tipOverrideResId = null,
                actionReady = false,
                navGeneration = generation,
                taskOutcomes = outcomes,
                coreCompleted = it.coreCompleted || enteringGate,
            )
        }
        if (enteringGate && !state.coreCompleted) {
            persistCoreMilestone(outcomes)
        }
        emitExpandedNav(completeNav, generation)
        emitEnterNav(nextStep, generation)
        maybeArmAfterRoute(nextStep, currentRoute)
    }

    private fun maybeEnterGateMilestone(step: TourStep, outcomes: Map<TourTaskId, TourTaskOutcome>) {
        if (step.type != TourStepType.GATE) return
        val state = _session.value
        if (!allTerminal(outcomes, state.definition)) return
        if (!state.coreCompleted) {
            _session.update { it.copy(coreCompleted = true) }
            persistCoreMilestone(outcomes)
        }
        _session.update { it.copy(actionReady = true) }
    }

    private fun allTerminal(
        outcomes: Map<TourTaskId, TourTaskOutcome>,
        definition: TourDefinition,
    ): Boolean =
        definition.actionTaskOrder.all { task ->
            when (outcomes[task] ?: TourTaskOutcome.Pending) {
                TourTaskOutcome.Completed, TourTaskOutcome.UserSkipped -> true
                TourTaskOutcome.Pending -> false
            }
        }

    private fun emitEnterNav(step: TourStep, generation: Long) {
        for (action in step.resolvedEnterNavActions()) {
            emitExpandedNav(action, generation)
        }
    }

    private fun emitExpandedNav(action: TourNavAction, generation: Long) {
        for (expanded in TourNavPlanner.expandNavAction(action, currentRoute)) {
            if (expanded != TourNavAction.NONE) {
                _navCommands.tryEmit(TourNavCommand(expanded, generation))
            }
        }
    }

    private fun persistCoreMilestone(outcomes: Map<TourTaskId, TourTaskOutcome> = _session.value.taskOutcomes) {
        val state = _session.value
        if (!allTerminal(outcomes, state.definition)) return
        val ended = clock()
        val result = TourRunResult(
            runId = state.runId ?: UUID.randomUUID().toString(),
            startedAtEpochMs = state.startedAtEpochMs ?: ended,
            endedAtEpochMs = ended,
            completedStepIds = state.completedStepIds.map { it.name },
            skippedStepIds = state.skippedStepIds.map { it.name },
            exitStepId = null,
            completedTour = true,
        )
        viewModelScope.launch {
            testerToolsStore.saveTourResult(result)
        }
    }

    private fun finishCompleted(
        extraCompleted: List<TourStepId> = emptyList(),
        extraSkipped: List<TourStepId> = emptyList(),
        reason: String? = null,
        taskOutcomes: Map<TourTaskId, TourTaskOutcome> = _session.value.taskOutcomes,
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
        val allowCompletedFlag = allTerminal(taskOutcomes, state.definition) || state.coreCompleted
        val result = TourRunResult(
            runId = state.runId ?: UUID.randomUUID().toString(),
            startedAtEpochMs = state.startedAtEpochMs ?: ended,
            endedAtEpochMs = ended,
            completedStepIds = completed.map { it.name },
            skippedStepIds = skipped.map { it.name },
            exitStepId = null,
            completedTour = allowCompletedFlag,
        )
        _session.value = state.copy(
            status = TourStatus.Completed,
            completedStepIds = completed,
            skippedStepIds = skipped,
            skipReasons = skipReasons,
            endedAtEpochMs = ended,
            waitingForTarget = false,
            actionReady = false,
            coreCompleted = allowCompletedFlag,
            taskOutcomes = taskOutcomes,
            stepIndex = state.steps.lastIndex.coerceAtLeast(0),
        )
        viewModelScope.launch {
            testerToolsStore.saveTourResult(result)
        }
    }

    companion object {
        fun routeEquals(route: String?, expected: String): Boolean = route == expected

        fun routeInSection(route: String?, prefix: String): Boolean {
            if (route == null) return false
            return route == prefix || route.startsWith("$prefix/")
        }

        fun targetsMatch(expected: TourTargetId, actual: TourTargetId): Boolean {
            if (expected == actual) return true
            if (expected == TourTargetId.SOUND_LIBRARY_ANY_ITEM) {
                return actual == TourTargetId.SOUND_LIBRARY_ANY_ITEM ||
                    actual == TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_ITEM
            }
            return false
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
