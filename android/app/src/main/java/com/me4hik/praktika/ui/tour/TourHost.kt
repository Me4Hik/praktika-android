package com.me4hik.praktika.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.me4hik.praktika.navigation.Routes

@Composable
fun TourHost(
    tourController: TourController,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val registry = remember { TourTargetRegistry() }
    val session by tourController.session.collectAsStateWithLifecycle()
    val targets by registry.targets.collectAsStateWithLifecycle()
    val active = session.isActive
    val latestSession by rememberUpdatedState(session)

    LaunchedEffect(session.runId) {
        if (session.runId != null) {
            registry.clear()
        }
    }

    LaunchedEffect(session.isActive) {
        if (!session.isActive) {
            registry.clear()
        }
    }

    LaunchedEffect(navController, tourController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            tourController.onRouteChanged(entry.destination.route)
        }
    }

    // collect (not collectLatest): ordered composite nav must not drop mid-sequence.
    // generation stamp drops commands from superseded transitions.
    LaunchedEffect(tourController) {
        tourController.navCommands.collect { command ->
            val liveGeneration = tourController.session.value.navGeneration
            if (command.generation < liveGeneration) return@collect
            when (command.action) {
                TourNavAction.NONE -> Unit
                TourNavAction.POP_TO_HOME -> {
                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                }
                TourNavAction.POP_ONCE -> {
                    navController.popBackStack()
                }
                TourNavAction.NAVIGATE_ARCHIVE -> {
                    if (navController.currentDestination?.route != Routes.ARCHIVE) {
                        navController.navigate(Routes.ARCHIVE) {
                            launchSingleTop = true
                        }
                    }
                }
                TourNavAction.NAVIGATE_ARCHIVE_DAYS -> {
                    if (navController.currentDestination?.route != Routes.ARCHIVE_DAYS) {
                        navController.navigate(Routes.ARCHIVE_DAYS) {
                            launchSingleTop = true
                        }
                    }
                }
                TourNavAction.NAVIGATE_SETTINGS -> {
                    if (navController.currentDestination?.route != Routes.SETTINGS) {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    }
                }
                TourNavAction.NAVIGATE_NOTIFICATIONS -> {
                    ensureSettingsParentThenNotifications(navController)
                }
                TourNavAction.NAVIGATE_SOUND_LIBRARY -> {
                    if (navController.currentDestination?.route != Routes.SOUND_LIBRARY) {
                        navController.navigate(Routes.SOUND_LIBRARY) {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(navController, active) {
        navController.enableOnBackPressed(!active)
        onDispose {
            navController.enableOnBackPressed(true)
        }
    }

    CompositionLocalProvider(
        LocalTourController provides tourController,
        LocalTourTargetRegistry provides registry,
        LocalTourChromeTopInsetDp provides TourChromePlacement.TOP_TARGET_INSET_DP,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (active) {
                val step = session.currentStep
                val hole = if (session.actionReady) {
                    step?.targetId?.let { targets[it] }
                } else {
                    null
                }
                TourOverlay(
                    session = session,
                    holeInWindow = hole,
                    onSkip = tourController::onSkip,
                    onExit = { tourController.onExit(session.currentStep?.id) },
                    onNext = tourController::onNext,
                    onDone = tourController::onFinishTour,
                    onFinish = tourController::onFinishTour,
                    onGateContinue = tourController::onGateContinueOptional,
                    onGateFinish = tourController::onGateFinish,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                        .testTag(TourOverlayTestTags.ROOT),
                )
            }
        }
        BackHandler(enabled = active) {
            val step = latestSession.currentStep
            if (step?.type == TourStepType.NAV_BACK) {
                navController.popBackStack()
            } else {
                tourController.onExit(step?.id)
            }
        }
    }
}

/**
 * Never open `settings/notifications` without `settings` on the back stack
 * (Notifications composable requires getBackStackEntry(SETTINGS)).
 */
internal fun ensureSettingsParentThenNotifications(navController: NavHostController) {
    val route = navController.currentDestination?.route
    if (route == Routes.SETTINGS_NOTIFICATIONS) return
    val hasSettings = runCatching {
        navController.getBackStackEntry(Routes.SETTINGS)
        true
    }.getOrDefault(false)
    if (!hasSettings) {
        navController.navigate(Routes.SETTINGS) {
            launchSingleTop = true
        }
    }
    if (navController.currentDestination?.route != Routes.SETTINGS_NOTIFICATIONS) {
        navController.navigate(Routes.SETTINGS_NOTIFICATIONS) {
            launchSingleTop = true
        }
    }
}

@Composable
private fun TourOverlay(
    session: TourSessionState,
    holeInWindow: androidx.compose.ui.geometry.Rect?,
    onSkip: () -> Unit,
    onExit: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onFinish: () -> Unit,
    onGateContinue: () -> Unit,
    onGateFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = session.currentStep ?: return
    val phase = step.uiPhase()
    val blockHole = step.type == TourStepType.SHOW_ONLY && step.targetId != null && session.actionReady
    val tipResId = session.effectiveTipResId ?: step.tipResId
    val tip = stringResource(tipResId)
    val cue = session.showActionCue && holeInWindow != null
    val isCompletion = phase == TourUiPhase.COMPLETION
    val showNext = step.completion == TourCompletion.ManualAdvance && !isCompletion
    val showDone = isCompletion
    val showManualAdvanceCue =
        session.actionReady && step.showsManualAdvanceCue() && (showNext || showDone)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val estimatedChromePx = with(density) {
            TourChromePlacement.DEFAULT_ESTIMATED_CHROME_HEIGHT_DP.dp.toPx()
        }
        // TOP ALWAYS — placeBelow is permanently false.
        val placeBelow = TourChromePlacement.placeBelow(
            holeInWindow = holeInWindow,
            overlayHeightPx = heightPx,
            estimatedChromeHeightPx = estimatedChromePx,
        )
        TourSpotlightLayer(
            holeInWindow = holeInWindow,
            blockHole = blockHole,
            showActionCue = cue,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (placeBelow) Alignment.BottomCenter else Alignment.TopCenter,
        ) {
            TourChrome(
                tip = tip,
                progressDisplay = session.progressDisplay,
                placeBelow = placeBelow,
                showSkip = step.showsChromeSkip(),
                showNext = showNext,
                showDone = showDone,
                showFinish = false,
                showExit = !isCompletion,
                showManualAdvanceCue = showManualAdvanceCue,
                showGateContinue = phase == TourUiPhase.GATE && session.allActionTasksTerminal(),
                showGateFinish = phase == TourUiPhase.GATE && session.allActionTasksTerminal(),
                onSkip = onSkip,
                onExit = onExit,
                onNext = onNext,
                onDone = {
                    // FINAL ManualAdvance: record step + finish via onNext last-step path.
                    if (isCompletion) onNext() else onDone()
                },
                onFinish = onFinish,
                onGateContinue = onGateContinue,
                onGateFinish = onGateFinish,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun TourEndStopsPreviewEffect(
    onStopPreview: () -> Unit,
) {
    val tourController = LocalTourController.current ?: return
    val stopPreview by rememberUpdatedState(onStopPreview)
    LaunchedEffect(tourController) {
        var wasActive = tourController.session.value.isActive
        tourController.session.collect { state ->
            if (wasActive && !state.isActive) {
                stopPreview()
            }
            wasActive = state.isActive
        }
    }
}
