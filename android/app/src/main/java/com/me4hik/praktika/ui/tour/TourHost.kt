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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.me4hik.praktika.navigation.Routes
import kotlinx.coroutines.flow.collectLatest

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
        navController.currentBackStackEntryFlow.collectLatest { entry ->
            tourController.onRouteChanged(entry.destination.route)
        }
    }

    LaunchedEffect(tourController) {
        tourController.navCommands.collectLatest { action ->
            when (action) {
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
                    if (navController.currentDestination?.route != Routes.SETTINGS_NOTIFICATIONS) {
                        navController.navigate(Routes.SETTINGS_NOTIFICATIONS) {
                            launchSingleTop = true
                        }
                    }
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

    // Sole system-back owner while tour is active; always restore on dispose / inactive.
    DisposableEffect(navController, active) {
        navController.enableOnBackPressed(!active)
        onDispose {
            navController.enableOnBackPressed(true)
        }
    }

    CompositionLocalProvider(
        LocalTourController provides tourController,
        LocalTourTargetRegistry provides registry,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (active) {
                val step = session.currentStep
                val hole = step?.targetId?.let { targets[it] }
                TourOverlay(
                    session = session,
                    holeInWindow = hole,
                    onSkip = tourController::onSkip,
                    onExit = { tourController.onExit(session.currentStep?.id) },
                    onNext = tourController::onNext,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                        .testTag(TourOverlayTestTags.ROOT),
                )
            }
        }
        // After NavHost (content) so LIFO dispatcher priority favors Tour over Nav.
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

@Composable
private fun TourOverlay(
    session: TourSessionState,
    holeInWindow: androidx.compose.ui.geometry.Rect?,
    onSkip: () -> Unit,
    onExit: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = session.currentStep ?: return
    val blockHole = step.type == TourStepType.SHOW_ONLY && step.targetId != null
    val tipResId = session.effectiveTipResId ?: step.tipResId
    val tip = stringResource(tipResId)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        TourSpotlightLayer(
            holeInWindow = holeInWindow,
            blockHole = blockHole,
            modifier = Modifier.fillMaxSize(),
        )
        val placeBelow = holeInWindow == null ||
            holeInWindow.bottom < heightPx * 0.55f
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (placeBelow) Alignment.BottomCenter else Alignment.TopCenter,
        ) {
            TourChrome(
                tip = tip,
                stepOrdinal = session.stepOrdinal,
                stepCount = session.stepCount,
                showNext = step.completion == TourCompletion.ManualAdvance,
                holeInWindow = holeInWindow,
                overlayHeightPx = heightPx,
                onSkip = onSkip,
                onExit = onExit,
                onNext = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Stops sound preview when an active tour ends while Sound Library stays composed.
 * Must be called from [com.me4hik.praktika.ui.settings.SoundLibraryScreen] with the real stop hook.
 */
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
