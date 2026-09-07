package com.me4hik.praktika.ui.tour

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(id: TourTargetId): Modifier = composed {
    val registry = LocalTourTargetRegistry.current
    val controller = LocalTourController.current
    if (registry == null) {
        return@composed this
    }
    val sessionFlow = remember(controller) {
        controller?.session ?: MutableStateFlow(TourSessionState())
    }
    val session by sessionFlow.collectAsStateWithLifecycle()
    val generation by registry.generation.collectAsStateWithLifecycle()
    val step = session.currentStep
    val isActiveTarget = session.isActive && step?.targetId == id
    val shouldBring = isActiveTarget && (step.bringIntoView || step.allowScroll)
    val requester = remember(id) { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var lastCoordinates by remember(id) { mutableStateOf<LayoutCoordinates?>(null) }

    DisposableEffect(id, registry) {
        onDispose { registry.remove(id) }
    }

    fun publishBoundsFrom(coords: LayoutCoordinates) {
        if (!coords.isAttached) return
        val bounds = coords.boundsInWindow()
        if (bounds.width > 0f && bounds.height > 0f) {
            registry.update(id, bounds)
            val latest = controller?.session?.value
            if (latest != null && latest.isActive && latest.currentStep?.targetId == id) {
                controller.notifyTargetAvailable(id)
            }
        }
    }

    // Re-publish after registry.clear() (generation bump) or new tour run.
    LaunchedEffect(generation, session.runId, id) {
        val coords = lastCoordinates ?: return@LaunchedEffect
        publishBoundsFrom(coords)
    }

    LaunchedEffect(isActiveTarget, shouldBring) {
        if (!isActiveTarget) return@LaunchedEffect
        if (shouldBring) {
            runCatching { requester.bringIntoView() }
            // Wait one Compose frame so scroll settle is reflected in layout.
            withFrameNanos { }
            val coords = lastCoordinates
            if (coords != null) {
                publishBoundsFrom(coords)
            }
        } else if (registry.bounds(id) != null) {
            controller?.notifyTargetAvailable(id)
        }
    }

    this
        .then(if (shouldBring) Modifier.bringIntoViewRequester(requester) else Modifier)
        .onGloballyPositioned { coordinates: LayoutCoordinates ->
            if (!coordinates.isAttached) return@onGloballyPositioned
            lastCoordinates = coordinates
            val bounds = coordinates.boundsInWindow()
            if (bounds.width > 0f && bounds.height > 0f) {
                registry.update(id, bounds)
                if (isActiveTarget) {
                    controller?.notifyTargetAvailable(id)
                    if (shouldBring) {
                        scope.launch {
                            runCatching { requester.bringIntoView() }
                            withFrameNanos { }
                            val settled = lastCoordinates
                            if (settled != null && settled.isAttached) {
                                val settledBounds = settled.boundsInWindow()
                                if (settledBounds.width > 0f && settledBounds.height > 0f) {
                                    registry.update(id, settledBounds)
                                    if (session.isActive && session.currentStep?.targetId == id) {
                                        controller?.notifyTargetAvailable(id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}
