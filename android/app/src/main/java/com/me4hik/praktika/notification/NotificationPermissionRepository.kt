// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission repository
// 10.08.2026 Post-release fixes cursor by Me4Hik START - split recovery state machine
package com.me4hik.praktika.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationPermissionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "praktika_notification_permission",
)

class NotificationPermissionRepository(
    private val context: Context,
    private val soundEnabledFlow: Flow<Boolean>,
) : NotificationPermissionPolicy {
    private var rationaleChecker: () -> Boolean = { false }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - permission UI refresh trigger
    private val _permissionStateRevision = MutableStateFlow(0L)
    override val permissionStateRevision: StateFlow<Long> = _permissionStateRevision.asStateFlow()

    override fun notifyPermissionStateChanged(source: String) {
        val nextRevision = _permissionStateRevision.value + 1L
        _permissionStateRevision.value = nextRevision
        if (DiagnosticsRecorder.isInitialized()) {
            DiagnosticsRecorder.get().record(
                category = DiagnosticCategory.PERMISSION,
                name = "permission_ui_refresh_trigger",
                metadata = mapOf(
                    "source" to source,
                    "revision" to nextRevision.toString(),
                ),
            )
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    override val permissionRequested: Flow<Boolean> = context.notificationPermissionDataStore.data
        .map { preferences ->
            preferences[PERMISSION_REQUESTED_KEY] ?: false
        }

    fun bindRationaleChecker(checker: () -> Boolean) {
        rationaleChecker = checker
    }

    override suspend fun markPermissionRequested() {
        context.notificationPermissionDataStore.edit { preferences ->
            preferences[PERMISSION_REQUESTED_KEY] = true
        }
    }

    suspend fun currentUiState(soundEnabled: Boolean): NotificationPermissionUiState {
        val requested = permissionRequested.first()
        return evaluateUiState(requested, soundEnabled)
    }

    override fun evaluateUiState(
        permissionRequested: Boolean,
        soundEnabled: Boolean,
    ): NotificationPermissionUiState {
        val channelId = selectedChannelId(soundEnabled)
        return NotificationPermissionStateResolver.resolve(
            supportsRuntimePermission = shouldRequestRuntimePermission(),
            runtimeGranted = hasRuntimePermission(),
            permissionEverRequested = permissionRequested,
            shouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale(),
            appNotificationsEnabled = areAppNotificationsEnabled(),
            selectedChannelBlocked = isChannelBlocked(channelId),
        )
    }

    override fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability {
        return when (state) {
            NotificationPermissionUiState.ENABLED -> NotificationDeliveryCapability.ENABLED
            NotificationPermissionUiState.NOT_REQUESTED,
            NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED,
            NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED,
            NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED,
            -> NotificationDeliveryCapability.DISABLED
        }
    }

    override fun areAppNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        if (!shouldRequestRuntimePermission()) {
            return false
        }
        return rationaleChecker()
    }

    override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean {
        return !isChannelBlocked(selectedChannelId(soundEnabled))
    }

    fun isChannelBlocked(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    override fun createAppNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    override fun createChannelSettingsIntent(soundEnabled: Boolean): Intent {
        val channelId = selectedChannelId(soundEnabled)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            createAppNotificationSettingsIntent()
        }
    }

    override fun shouldRequestRuntimePermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    override fun hasRuntimePermission(): Boolean {
        if (!shouldRequestRuntimePermission()) {
            return areAppNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun selectedChannelId(soundEnabled: Boolean): String {
        // Permission / channel-blocked checks follow the due (HIGH) channels used for live questions.
        return AndroidPracticeNotificationPresenter.dueChannelId(soundEnabled)
    }

    private companion object {
        val PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
