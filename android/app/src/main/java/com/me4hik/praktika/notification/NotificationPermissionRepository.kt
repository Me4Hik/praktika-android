// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - permission repository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationPermissionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "praktika_notification_permission",
)

class NotificationPermissionRepository(
    private val context: Context,
    private val soundEnabledFlow: Flow<Boolean>,
) : NotificationPermissionPolicy {
    override val permissionRequested: Flow<Boolean> = context.notificationPermissionDataStore.data
        .map { preferences ->
            preferences[PERMISSION_REQUESTED_KEY] ?: false
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
        if (!areAppNotificationsEnabled()) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionRequested) {
                NotificationPermissionUiState.NOT_REQUESTED
            } else {
                NotificationPermissionUiState.DENIED_OR_DISABLED
            }
        }
        val channelId = if (soundEnabled) {
            PracticeNotificationChannels.SOUND
        } else {
            PracticeNotificationChannels.SILENT
        }
        if (isChannelBlocked(channelId)) {
            return NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED
        }
        return NotificationPermissionUiState.ENABLED
    }

    override fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability {
        return when (state) {
            NotificationPermissionUiState.ENABLED -> NotificationDeliveryCapability.ENABLED
            NotificationPermissionUiState.NOT_REQUESTED,
            NotificationPermissionUiState.DENIED_OR_DISABLED,
            NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED,
            -> NotificationDeliveryCapability.DISABLED
        }
    }

    override fun areAppNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
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
        val channelId = if (soundEnabled) {
            PracticeNotificationChannels.SOUND
        } else {
            PracticeNotificationChannels.SILENT
        }
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

    private companion object {
        val PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
