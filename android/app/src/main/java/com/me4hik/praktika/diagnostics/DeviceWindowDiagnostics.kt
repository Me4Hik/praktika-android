// 03.10.2026 Tablet-07 problem report window diagnostics cursor by Me4Hik START
package com.me4hik.praktika.diagnostics

import android.content.Context
import android.content.res.Configuration
import android.graphics.Insets
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import com.me4hik.praktika.ui.question.questionWidthBucketFor
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * App-window / configuration metrics for layout bug repro.
 * Null inset/window fields mean "unavailable", not zero.
 */
data class DeviceWindowDiagnosticsSnapshot(
    val windowWidthPx: Int? = null,
    val windowHeightPx: Int? = null,
    val screenWidthDp: Int? = null,
    val screenHeightDp: Int? = null,
    val smallestScreenWidthDp: Int? = null,
    val density: Float? = null,
    val densityDpi: Int? = null,
    val fontScale: Float? = null,
    val orientation: String = ORIENTATION_UNDEFINED,
    val insetStatusBarsDp: Int? = null,
    val insetNavigationBarsDp: Int? = null,
    val insetTappableElementDp: Int? = null,
    val insetSystemGesturesDp: Int? = null,
    val questionWidthBucket: String? = null,
) {
    companion object {
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_UNDEFINED = "undefined"
    }
}

fun interface DeviceWindowDiagnosticsProvider {
    fun collect(): DeviceWindowDiagnosticsSnapshot
}

class AndroidDeviceWindowDiagnosticsProvider(
    private val context: Context,
) : DeviceWindowDiagnosticsProvider {
    override fun collect(): DeviceWindowDiagnosticsSnapshot {
        return runCatching { collectOrThrow() }
            .getOrElse { DeviceWindowDiagnosticsSnapshot() }
    }

    private fun collectOrThrow(): DeviceWindowDiagnosticsSnapshot {
        val appContext = context.applicationContext
        val resources = appContext.resources
        val configuration = resources.configuration
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density.takeIf { it > 0f } ?: 1f

        val windowBounds = readWindowBoundsPx(appContext)
        val insets = readInsetsDp(appContext, density)

        val widthDp = configuration.screenWidthDp.takeIf { it > 0 }
        val heightDp = configuration.screenHeightDp.takeIf { it > 0 }
        val smallestWidthDp = configuration.smallestScreenWidthDp.takeIf { it > 0 }

        return DeviceWindowDiagnosticsSnapshot(
            windowWidthPx = windowBounds?.first,
            windowHeightPx = windowBounds?.second,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            smallestScreenWidthDp = smallestWidthDp,
            density = displayMetrics.density.takeIf { it > 0f },
            densityDpi = configuration.densityDpi.takeIf { it > 0 }
                ?: displayMetrics.densityDpi.takeIf { it > 0 },
            fontScale = configuration.fontScale.takeIf { it > 0f },
            orientation = orientationLabel(configuration.orientation),
            insetStatusBarsDp = insets?.statusBarsDp,
            insetNavigationBarsDp = insets?.navigationBarsDp,
            insetTappableElementDp = insets?.tappableElementDp,
            insetSystemGesturesDp = insets?.systemGesturesDp,
            questionWidthBucket = widthDp?.let { questionWidthBucketFor(it).name },
        )
    }

    private fun readWindowBoundsPx(appContext: Context): Pair<Int, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return runCatching {
            val windowManager = appContext.getSystemService(WindowManager::class.java) ?: return null
            val bounds = windowManager.currentWindowMetrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            if (width <= 0 || height <= 0) {
                null
            } else {
                width to height
            }
        }.getOrNull()
    }

    private data class InsetsDp(
        val statusBarsDp: Int?,
        val navigationBarsDp: Int?,
        val tappableElementDp: Int?,
        val systemGesturesDp: Int?,
    )

    private fun readInsetsDp(appContext: Context, density: Float): InsetsDp? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }
        return runCatching {
            val windowManager = appContext.getSystemService(WindowManager::class.java) ?: return null
            val windowInsets = windowManager.currentWindowMetrics.windowInsets
            InsetsDp(
                statusBarsDp = insetMaxDp(windowInsets.getInsets(WindowInsets.Type.statusBars()), density),
                navigationBarsDp = insetMaxDp(
                    windowInsets.getInsets(WindowInsets.Type.navigationBars()),
                    density,
                ),
                tappableElementDp = insetMaxDp(
                    windowInsets.getInsets(WindowInsets.Type.tappableElement()),
                    density,
                ),
                systemGesturesDp = insetMaxDp(
                    windowInsets.getInsets(WindowInsets.Type.systemGestures()),
                    density,
                ),
            )
        }.getOrNull()
    }

    private fun insetMaxDp(insets: Insets, density: Float): Int {
        val px = max(
            max(insets.left, insets.right),
            max(insets.top, insets.bottom),
        )
        return (px / density).roundToInt()
    }

    private fun orientationLabel(orientation: Int): String {
        return when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> DeviceWindowDiagnosticsSnapshot.ORIENTATION_PORTRAIT
            Configuration.ORIENTATION_LANDSCAPE -> DeviceWindowDiagnosticsSnapshot.ORIENTATION_LANDSCAPE
            else -> DeviceWindowDiagnosticsSnapshot.ORIENTATION_UNDEFINED
        }
    }
}

internal fun JSONObject.putDeviceWindowDiagnostics(snapshot: DeviceWindowDiagnosticsSnapshot) {
    snapshot.windowWidthPx?.let { put("windowWidthPx", it) }
    snapshot.windowHeightPx?.let { put("windowHeightPx", it) }
    snapshot.screenWidthDp?.let { put("screenWidthDp", it) }
    snapshot.screenHeightDp?.let { put("screenHeightDp", it) }
    snapshot.smallestScreenWidthDp?.let { put("smallestScreenWidthDp", it) }
    snapshot.density?.let { put("density", it.toDouble()) }
    snapshot.densityDpi?.let { put("densityDpi", it) }
    snapshot.fontScale?.let { put("fontScale", it.toDouble()) }
    put("orientation", snapshot.orientation)
    snapshot.insetStatusBarsDp?.let { put("insetStatusBarsDp", it) }
    snapshot.insetNavigationBarsDp?.let { put("insetNavigationBarsDp", it) }
    snapshot.insetTappableElementDp?.let { put("insetTappableElementDp", it) }
    snapshot.insetSystemGesturesDp?.let { put("insetSystemGesturesDp", it) }
    snapshot.questionWidthBucket?.let { put("questionWidthBucket", it) }
}
// 03.10.2026 Tablet-07 problem report window diagnostics cursor by Me4Hik END
