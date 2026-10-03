// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

import android.content.Context
import android.os.Build
import com.me4hik.praktika.BuildConfig
import com.me4hik.praktika.notification.PracticeNotificationChannels
import com.me4hik.praktika.runtime.PraktikaRuntime
import com.me4hik.praktika.runtime.RuntimeMode
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticSnapshotBuilder(
    private val context: Context,
    private val identityStore: DiagnosticIdentityStore,
    private val reportStore: DiagnosticReportStore,
    private val windowDiagnosticsProvider: DeviceWindowDiagnosticsProvider =
        AndroidDeviceWindowDiagnosticsProvider(context),
) {
    suspend fun build(
        runtime: PraktikaRuntime,
        recorder: DiagnosticsRecorder,
        testerComment: String?,
    ): JSONObject {
        val permissionRepository = runtime.notificationPermissionRepository
        val permissionRequested = permissionRepository.permissionRequested.first()
        val soundEnabled = runtime.soundPreferenceRepository.soundEnabled.first()
        val permissionUiState = permissionRepository.evaluateUiState(
            permissionRequested = permissionRequested,
            soundEnabled = soundEnabled,
        )
        val practiceSnapshot = runtime.practiceReadRepository.observeSnapshot().first()
        val scheduleSnapshot = runtime.scheduleReadRepository.observeSchedule().first()
        val occurrence = practiceSnapshot.incompleteOccurrence
        val recentEvents = recorder.getRecentEvents(limit = 150)
        val lastError = recorder.getMostRecentError()

        return JSONObject().apply {
            put("versionCode", BuildConfig.VERSION_CODE)
            put("versionName", BuildConfig.VERSION_NAME)
            put("packageName", context.packageName)
            put("runtimeMode", runtime.mode.name.lowercase())
            put("environment", if (runtime.mode == RuntimeMode.ACCELERATED) "accelerated" else "production")
            put("deviceManufacturer", Build.MANUFACTURER ?: "unknown")
            put("deviceModel", Build.MODEL ?: "unknown")
            put("androidRelease", Build.VERSION.RELEASE ?: "unknown")
            put("androidSdk", Build.VERSION.SDK_INT)
            put("timezone", TimeZone.getDefault().id)
            put("systemTimestampMs", System.currentTimeMillis())
            put("installId", identityStore.getOrCreateInstallIdAsync())
            put("currentRoute", NavigationRouteTracker.currentRoute ?: "")
            put("previousRoute", NavigationRouteTracker.previousRoute ?: "")
            put("notificationPermissionGranted", permissionRepository.hasRuntimePermission())
            put("appNotificationsEnabled", permissionRepository.areAppNotificationsEnabled())
            put("permissionRequestedFlag", permissionRequested)
            put("permissionUiState", permissionUiState.name)
            put("soundChannelImportance", channelImportance(PracticeNotificationChannels.SOUND))
            put("silentChannelImportance", channelImportance(PracticeNotificationChannels.SILENT))
            put("practiceStarted", practiceSnapshot.practiceState.isPracticeStarted)
            put("practicePaused", practiceSnapshot.practiceState.isPaused)
            put("currentCycleNumber", practiceSnapshot.practiceState.currentCycleNumber)
            put("activeZoneId", practiceSnapshot.practiceState.activeZoneId)
            put(
                "scheduleSlotMinutes",
                JSONArray(scheduleSnapshot.slots.map { it.timeOfDayMinutes }),
            )
            if (occurrence != null) {
                put("occurrenceId", occurrence.id)
                put("occurrenceStatus", occurrence.status.name)
                put("occurrencePlannedAt", occurrence.plannedAtEpochMillis)
                put("occurrenceAvailableUntil", occurrence.availableUntilEpochMillis)
            }
            val trimmedComment = testerComment?.trim()?.take(500).orEmpty()
            if (trimmedComment.isNotEmpty()) {
                put("testerComment", trimmedComment)
            }
            putWindowDiagnosticsSafely()
            put("recentEvents", DiagnosticSnapshotJson.eventsArray(recentEvents))
            if (lastError != null) {
                put("lastError", JSONObject(lastError.metadata).apply {
                    put("name", lastError.name)
                    put("category", lastError.category.name)
                    put("seq", lastError.seq)
                })
            }
        }
    }

    private fun JSONObject.putWindowDiagnosticsSafely() {
        runCatching {
            putDeviceWindowDiagnostics(windowDiagnosticsProvider.collect())
        }
    }

    fun saveLocally(snapshot: JSONObject) = reportStore.saveReport(snapshot)

    private fun channelImportance(channelId: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "legacy"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = manager.getNotificationChannel(channelId) ?: return "missing"
        return channel.importance.toString()
    }

}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
